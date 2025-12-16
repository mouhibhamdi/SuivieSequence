package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class SharedSequenceGapAnalyzer {

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("([A-Z]+PGWb)(\\d{3})(\\d{5})(\\d{14})\\.dat\\.gz");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    static class CdrFile {
        String type;
        String site;
        int sequence;
        LocalDateTime ts;
        String filename;
        LocalDate day;

        CdrFile(String type, String site, int sequence, LocalDateTime ts, String filename) {
            this.type = type;
            this.site = site;
            this.sequence = sequence;
            this.ts = ts;
            this.filename = filename;
            this.day = ts.toLocalDate();
        }

        String groupKeyGlobal() {
            return type + "-" + site;
        }
    }

    public static void main(String[] args) throws IOException {

        Path inputDirectory = Paths.get("src/test/resources/PGW_Sotelma/");
        Path outputReport = inputDirectory.resolve("rapport_PGW_Sotelma.txt");

        List<CdrFile> allFiles = new ArrayList<>();

        // ===== Lire fichiers =====
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDirectory, "*.txt")) {
            for (Path file : stream) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    Matcher m = FILENAME_PATTERN.matcher(trimmed);

                    if (m.matches()) {
                        String type = m.group(1);
                        String site = m.group(2);
                        int seq = Integer.parseInt(m.group(3));
                        LocalDateTime ts = LocalDateTime.parse(m.group(4), DATE_FORMATTER);

                        allFiles.add(new CdrFile(type, site, seq, ts, trimmed));
                    }
                }
            }
        }

        // ===== Regroupement par type-site) =====
        Map<String, List<CdrFile>> grouped = new TreeMap<>();
        for (CdrFile f : allFiles) grouped.computeIfAbsent(f.groupKeyGlobal(), k -> new ArrayList<>()).add(f);

        Map<String, List<String>> missingPerGroup = new TreeMap<>();
        Map<String, Integer> missingCountPerGroup = new TreeMap<>();


        // ===== ANALYSE =====
        for (String groupKey : grouped.keySet()) {

            List<CdrFile> list = grouped.get(groupKey);

            // Tri global :sequence, puis timestamp
            list.sort(Comparator.comparingInt((CdrFile f) -> f.sequence)
                    .thenComparing(f -> f.ts));

            // ==== Construire flux journaliers d'abord ====
            List<List<CdrFile>> dailyFlux = new ArrayList<>();
            List<CdrFile> currentFlux = new ArrayList<>();
            currentFlux.add(list.get(0));

            for (int i = 1; i < list.size(); i++) {
                CdrFile prev = list.get(i - 1);
                CdrFile curr = list.get(i);
                boolean newFlux = false;

                if (curr.sequence < prev.sequence || curr.sequence - prev.sequence > 2000) newFlux = true;
                if (Duration.between(prev.ts, curr.ts).toHours() > 2) newFlux = true;

                if (newFlux) {
                    dailyFlux.add(new ArrayList<>(currentFlux));
                    currentFlux.clear();
                }
                currentFlux.add(curr);
            }
            dailyFlux.add(currentFlux);

            // ==== Fusion inter-jour ====
            List<List<CdrFile>> mergedFlux = getLists(dailyFlux);

            // ==== calcul des gaps dans les flux fusionnés ====
            List<String> missingRanges = new ArrayList<>();
            int totalMissing = 0;

            for (List<CdrFile> flux : mergedFlux) {
                List<Integer> seq = flux.stream().map(f -> f.sequence).sorted().toList();

                for (int i = 1; i < seq.size(); i++) {
                    int prev = seq.get(i - 1);
                    int curr = seq.get(i);

                    if (curr - prev > 1) {
                        missingRanges.add((prev + 1) + " → " + (curr - 1));
                        totalMissing += (curr - prev - 1);
                    }
                }
            }

            missingPerGroup.put(groupKey, missingRanges);
            missingCountPerGroup.put(groupKey, totalMissing);
        }

        // ===== Écriture Rapport =====
        try (BufferedWriter writer = Files.newBufferedWriter(outputReport)) {

            int totalFilesMissing = missingCountPerGroup.values().stream().mapToInt(Integer::intValue).sum();

            writer.write("===== STATISTIQUES GLOBALES =====\n");
            writer.write("Total fichiers analysés : " + allFiles.size() + "\n");
            writer.write("Total fichiers manquants : " + totalFilesMissing + "\n\n");

            // ===== DETAIL PAR GROUPE =====
            for (String group : missingPerGroup.keySet()) {

                writer.write("Groupe : " + group + "\n");

                // gaps
                List<String> ranges = missingPerGroup.get(group);
                int missing = missingCountPerGroup.get(group);

                if (ranges.isEmpty()) {
                    writer.write("  Aucun fichier manquant ✓\n\n");
                } else {
                    writer.write("  Séquences manquantes :\n");
                    for (String r : ranges) writer.write("    - " + r + "\n");
                    writer.write("  Total manquants : " + missing + "\n\n");
                }
            }
        }

        System.out.println("Rapport généré : " + outputReport.getFileName());
    }

    private static List<List<CdrFile>> getLists(List<List<CdrFile>> dailyFlux) {
        List<List<CdrFile>> mergedFlux = new ArrayList<>();
        List<CdrFile> currMerged = new ArrayList<>(dailyFlux.get(0));

        for (int i = 1; i < dailyFlux.size(); i++) {

            List<CdrFile> next = dailyFlux.get(i);

            int lastSeq = currMerged.get(currMerged.size() - 1).sequence;
            int firstSeqNext = next.get(0).sequence;

            if (firstSeqNext == lastSeq + 1) {
                currMerged.addAll(next);
            } else {
                mergedFlux.add(currMerged);
                currMerged = new ArrayList<>(next);
            }
        }
        mergedFlux.add(currMerged);
        return mergedFlux;
    }
}
