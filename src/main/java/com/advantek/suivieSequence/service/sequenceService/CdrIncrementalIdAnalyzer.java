package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CdrIncrementalIdAnalyzer {


    private static final Pattern FILE_PATTERN = Pattern.compile("^(SITE1_MSC\\d+PGWCDR)(\\d{8})\\.dat\\.gz$");
    private static final Pattern TXT_FILE_DATE_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\.txt$");
    private static final long SEUIL = 1_000L;

    public static void main(String[] args) throws IOException {
        File inputFolder = new File("src/test/resources/PGW_Orange/");
        File outputReportFile = new File("src/test/resources/rapport-PGW-orange.txt");

        if (!inputFolder.isDirectory()) {
            System.err.println("Le dossier spécifié n'existe pas ou n'est pas un dossier.");
            return;
        }

        File[] txtFiles = inputFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (txtFiles == null || txtFiles.length == 0) {
            System.out.println("Aucun fichier .txt trouvé dans le dossier.");
            return;
        }

        Map<String, List<FileEntry>> allEntriesByClass = new HashMap<>();
        Map<String, Integer> filesPerDay = new TreeMap<>();

        for (File txtFile : txtFiles) {
            String fileName = txtFile.getName();
            Matcher dateMatcher = TXT_FILE_DATE_PATTERN.matcher(fileName);
            if (!dateMatcher.matches()) continue;

            String fileDate = dateMatcher.group(1);

            List<String> lines = Files.readAllLines(txtFile.toPath());
            filesPerDay.put(fileDate, lines.size());

            for (String line : lines) {
                Matcher matcher = FILE_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    String type = matcher.group(1);
                    long seq = Long.parseLong(matcher.group(2));
                    FileEntry entry = new FileEntry(line.trim(), type, seq);
                    allEntriesByClass.computeIfAbsent(type, k -> new ArrayList<>()).add(entry);
                }
            }
        }

        Map<String, List<String>> classMissingRanges = new TreeMap<>();
        Map<String, Long> classMissingCount = new TreeMap<>();
        long totalAnalyzed = 0;
        long totalMissing = 0;

        for (String type : new TreeSet<>(allEntriesByClass.keySet())) {
            List<FileEntry> entries = allEntriesByClass.get(type);
            entries.sort(Comparator.comparingLong(FileEntry::getCombinedIndex));

            totalAnalyzed += entries.size();
            List<String> missingRanges = new ArrayList<>();
            long missingForClass = 0;

            long prevCombined = -1;
            for (FileEntry entry : entries) {
                long currentCombined = entry.getCombinedIndex();
                if (prevCombined != -1) {
                    long gap = currentCombined - prevCombined - 1;
                    if (gap > 0 && gap <= SEUIL) {
                        missingRanges.add(formatRange(prevCombined + 1, currentCombined - 1));
                        missingForClass += gap;
                    }
                }
                prevCombined = currentCombined;
            }

            classMissingRanges.put(type, missingRanges);
            classMissingCount.put(type, missingForClass);
            totalMissing += missingForClass;
        }

        // Écriture du rapport
        try (PrintWriter writer = new PrintWriter(outputReportFile)) {

            writer.println("===== PER DAY ANALYSIS =====");
            for (String date : filesPerDay.keySet()) {
                writer.println("Date: " + date + " | Total files: " + filesPerDay.get(date));
            }
            writer.println();
            writer.println("===== MISSING SEQUENCES PER CLASS =====");
            for (String type : classMissingRanges.keySet()) {
                List<String> missing = classMissingRanges.get(type);
                long missingCount = classMissingCount.get(type);

                writer.println("Class " + type + ":");
                if (missing.isEmpty()) {
                    writer.println("  No missing sequences ");
                } else {
                    writer.println("  Missing sequences: " + String.join(", ", missing));
                    writer.println("  Total missing files: " + missingCount);
                }
                writer.println();
            }
            writer.println("===== SUMMARY =====");
            writer.println("Total analysed files: " + totalAnalyzed);
            writer.println("Total missing sequences: " + totalMissing);
        }

        System.out.println("Global report saved to: " + outputReportFile.getAbsolutePath());
    }

    private static String formatRange(long start, long end) {
        return (start == end) ? String.valueOf(start) : start + "–" + end;
    }

    private static class FileEntry {
        String name;
        String type;
        long seq;

        public FileEntry(String name, String type, long seq) {
            this.name = name;
            this.type = type;
            this.seq = seq;
        }

        public long getCombinedIndex() {
            return seq;
        }
    }
}
