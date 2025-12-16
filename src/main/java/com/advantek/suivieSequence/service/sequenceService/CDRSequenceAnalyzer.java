package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CDRSequenceAnalyzer {

    // format : <SOURCE>_<YYYYMMDDHHMMSS>_<SEQUENCE>.dat.gz
    private static final Pattern PATTERN =
            Pattern.compile("^(DC1-PGWCDR_AP64)_(\\d{14})_(\\d+)\\.dat\\.gz$", Pattern.CASE_INSENSITIVE);

    private static final long SEUIL = 1_000L;

    public static void main(String[] args) throws IOException {

        String inputFolder = "src/test/resources/PGW_chinguitel/";
        String reportFile = "src/test/resources/rapport-PGW-chinguitel.txt";

        List<CDRFile> allFiles = new ArrayList<>();
        Map<String, Integer> filesPerDay = new TreeMap<>();

        // Lecture de tous les fichiers journaliers
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(inputFolder), "*.txt")) {
            for (Path file : stream) {

                String day = file.getFileName().toString().replace(".txt", "");
                List<CDRFile> dailyFiles = processDailyFile(file);

                allFiles.addAll(dailyFiles);
                filesPerDay.put(day, dailyFiles.size());

                System.out.println("Processed: " + file.getFileName());
            }
        }

        // Détection des séquences manquantes
        List<String> missingRanges = findMissingRanges(allFiles);
        long totalMissing = missingRanges.stream()
                .mapToLong(CDRSequenceAnalyzer::countMissingFromRange)
                .sum();

        // Écriture du rapport final
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(reportFile))) {

            writer.write("===== PER DAY ANALYSIS =====\n");
            for (String day : filesPerDay.keySet()) {
                writer.write(String.format("Date: %s | Files processed: %d%n",
                        day, filesPerDay.get(day)));
            }

            writer.write("\n===== MISSING SEQUENCES =====\n");
            if (missingRanges.isEmpty()) {
                writer.write("  No missing sequences\n\n");
            } else {
                writer.write("  Missing sequences: " + String.join(", ", missingRanges) + "\n");
                writer.write("  Total missing: " + totalMissing + "\n\n");
            }

            writer.write("===== SUMMARY =====\n");
            writer.write("Total files analysed: " + allFiles.size() + "\n");
            writer.write("Total missing sequences: " + totalMissing + "\n");

            double loss = (totalMissing * 100.0) / Math.max(allFiles.size(), 1);
            writer.write(String.format("Loss rate: %.5f %%\n", loss));
        }

        System.out.println("Report saved → " + reportFile);
    }

    // Lecture d'un fichier journalier contenant la liste des noms de fichiers
    private static List<CDRFile> processDailyFile(Path dailyFile) throws IOException {

        List<CDRFile> files = new ArrayList<>();
        List<String> lines = Files.readAllLines(dailyFile);

        for (String line : lines) {
            String name = line.trim();

            Matcher m = PATTERN.matcher(name);
            if (m.find()) {
                long sequence = Long.parseLong(m.group(3)); // Groupe 3 = séquence
                String dateTime = m.group(2);               // Groupe 2 = YYYYMMDDHHMMSS
                files.add(new CDRFile(sequence, name, dateTime));
            }
        }

        return files;
    }

    // Détection des séquences manquantes
    private static List<String> findMissingRanges(List<CDRFile> files) {
        List<String> ranges = new ArrayList<>();
        if (files.isEmpty()) return ranges;

        files.sort(Comparator.comparingLong(f -> f.sequence));

        long prev = files.get(0).sequence;

        for (int i = 1; i < files.size(); i++) {
            long curr = files.get(i).sequence;
            long diff = curr - prev;

            if (diff > 1 && diff <= SEUIL) {
                ranges.add(formatRange(prev + 1, curr - 1));
            }
            prev = curr;
        }

        return ranges;
    }

    private static String formatRange(long a, long b) {
        return (a == b) ? String.valueOf(a) : a + "–" + b;
    }

    private static long countMissingFromRange(String range) {
        if (range.contains("–")) {
            String[] p = range.split("–");
            return Long.parseLong(p[1]) - Long.parseLong(p[0]) + 1;
        }
        return 1;
    }

    // Classe représentant un fichier CDR
    private static class CDRFile {
        long sequence;
        String name;
        String dateTime;

        CDRFile(long seq, String name, String dateTime) {
            this.sequence = seq;
            this.name = name;
            this.dateTime = dateTime;
        }
    }
}
