package com.advantek.suivieSequence.service.sequenceService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoucherGapAnalyzer {

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("vou_(\\d{3})_\\d{3}_\\d{5}_(\\d{14})_(\\d+)\\.unl\\.gz");

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_SEQUENCE = 99999;

    static class CdrFile {
        String category;
        LocalDate date;
        LocalDateTime ts;
        int sequence;
        String fileName;

        CdrFile(String category, LocalDateTime ts, int sequence, String fileName) {
            this.category = category;
            this.ts = ts;
            this.date = ts.toLocalDate();
            this.sequence = sequence;
            this.fileName = fileName;
        }
    }

    // Flux pour chaque catégorie
    static class Flux {
        int startSeq;
        int endSeq;
        int missingFiles;
        List<CdrFile> files = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {

        Path inputDir = Paths.get("src/test/resources/IN/");
        Path reportFile = inputDir.resolve("rapport_voucher.txt");
        Path sortedListFile = inputDir.resolve("fichiers_tries.txt");

        List<CdrFile> allFiles = new ArrayList<>();

        // 1. Lecture des fichiers
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.txt")) {
            for (Path file : stream) {
                for (String line : Files.readAllLines(file)) {
                    String f = line.trim();
                    Matcher m = FILENAME_PATTERN.matcher(f);
                    if (m.matches()) {
                        String category = m.group(1);
                        LocalDateTime ts = LocalDateTime.parse(m.group(2), TS_FORMAT);
                        int seq = Integer.parseInt(m.group(3));
                        allFiles.add(new CdrFile(category, ts, seq, f));
                    }
                }
            }
        }

        // 2. Grouper par catégorie
        Map<String, List<CdrFile>> groupedByCategory = new TreeMap<>();
        for (CdrFile f : allFiles) {
            groupedByCategory.computeIfAbsent(f.category, k -> new ArrayList<>()).add(f);
        }

        // 3. Trier par date et timestamp
        for (List<CdrFile> list : groupedByCategory.values()) {
            list.sort(Comparator.comparing((CdrFile c) -> c.date).thenComparing(c -> c.ts));
        }

        // 4. Calcul des flux et gaps
        Map<String, List<Flux>> fluxByCategory = new LinkedHashMap<>();
        Map<String, Integer> missingByCategory = new HashMap<>();

        for (var catEntry : groupedByCategory.entrySet()) {
            String category = catEntry.getKey();
            List<CdrFile> files = catEntry.getValue();

            List<Flux> fluxList = new ArrayList<>();
            Flux currentFlux = new Flux();
            currentFlux.files.add(files.get(0));
            currentFlux.startSeq = files.get(0).sequence;
            currentFlux.endSeq = files.get(0).sequence;
            int missing = 0;

            for (int i = 1; i < files.size(); i++) {
                int prev = files.get(i - 1).sequence;
                int curr = files.get(i).sequence;

                int gap;
                if (prev < curr) {
                    gap = curr - prev - 1;
                } else if (prev > curr) {
                    // rollover
                    gap = (MAX_SEQUENCE - prev) + curr;
                } else {
                    gap = 0; // doublon
                }

                if (gap > MAX_SEQUENCE / 2) {
                    gap = 0; // ignorer gaps aberrants
                }

                if (gap > 0) {
                    missing += gap;
                }

                currentFlux.files.add(files.get(i));
                currentFlux.endSeq = curr;
            }

            fluxList.add(currentFlux);
            fluxByCategory.put(category, fluxList);
            missingByCategory.put(category, missing);
        }

        // 5. Génération des fichiers triés
        generateSortedFilesList(sortedListFile.toString(), groupedByCategory);

        // 6. Génération du rapport
        generateReportFile(reportFile.toString(), groupedByCategory, missingByCategory);

        System.out.println("Rapport généré : " + reportFile);
        System.out.println("Liste fichiers triés générée : " + sortedListFile);
    }

    private static void generateSortedFilesList(String outputPath, Map<String, List<CdrFile>> grouped) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))) {
            writer.write("===== LISTE COMPLETE DES FICHIERS TRIES =====\n\n");
            for (var catEntry : grouped.entrySet()) {
                String category = catEntry.getKey();
                List<CdrFile> list = catEntry.getValue();
                writer.write("Category : " + category + "\n");
                for (CdrFile f : list) {
                    writer.write("    - [" + f.date + "] Seq=" + f.sequence + " | " + f.fileName + "\n");
                }
                writer.write("\n");
            }
        }
    }

    private static void generateReportFile(
            String outputPath,
            Map<String, List<CdrFile>> grouped,
            Map<String, Integer> missingByCategory
    ) throws IOException {

        int totalFilesGlobal = 0;
        int totalMissingGlobal = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))) {
            writer.write("===== RAPPORT SEQUENCES VOUCHER =====\n\n");
            writer.write("===== STATISTIQUES PAR CATEGORY =====\n");

            for (var catEntry : grouped.entrySet()) {
                String category = catEntry.getKey();
                List<CdrFile> files = catEntry.getValue();

                totalFilesGlobal += files.size();
                int missing = missingByCategory.getOrDefault(category, 0);
                totalMissingGlobal += missing;

                writer.write(String.format(
                        "Category %s : Total fichiers analysés = %d | Fichiers manquants = %d%n",
                        category, files.size(), missing
                ));

                // --- Affichage des flux ---
                writer.write("  Flux détectés :\n");
                List<CdrFile> sortedFiles = new ArrayList<>(files);
                sortedFiles.sort(Comparator.comparing(c -> c.ts));

                int prevSeq = sortedFiles.get(0).sequence;
                LocalDateTime prevTs = sortedFiles.get(0).ts;

// Premier fichier : pas de manquant avant
                writer.write(String.format("    - [%s] Seq %d → %d | manquants : 0%n",
                        prevTs.toLocalDate(), prevSeq, prevSeq));

                for (int i = 1; i < sortedFiles.size(); i++) {
                    CdrFile curr = sortedFiles.get(i);
                    int currSeq = curr.sequence;

                    int gap;
                    if (currSeq > prevSeq) {
                        gap = currSeq - prevSeq - 1;
                    } else if (prevSeq > currSeq) {
                        // rollover
                        gap = (MAX_SEQUENCE - prevSeq) + currSeq;
                    } else {
                        gap = 0; // doublon
                    }

                    if (gap < 0 || gap > MAX_SEQUENCE / 2) gap = 0; // ignorer gaps aberrants

                    writer.write(String.format("    - [%s] Seq %d → %d | manquants : %d%n",
                            curr.ts.toLocalDate(), prevSeq, currSeq, gap));

                    prevSeq = currSeq;
                    prevTs = curr.ts;
                }


                writer.write("\n");
            }

            writer.write("===== STATISTIQUES GLOBALES =====\n");
            writer.write(String.format("Total fichiers analysés : %d%n", totalFilesGlobal));
            writer.write(String.format("Total fichiers manquants : %d%n", totalMissingGlobal));
        }
    }
}
