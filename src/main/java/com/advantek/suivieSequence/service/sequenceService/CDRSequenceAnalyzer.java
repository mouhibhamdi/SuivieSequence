package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CDRSequenceAnalyzer {

    private static final Pattern FILENAME_PATTERN = Pattern.compile("(Nokia\\d+)_CF(\\d+)\\.DAT\\.gz");
    private static final long SEUIL = 1_000L;

    public static void main(String[] args) throws IOException {
        String inputFolder = "src/test/resources/MSC/";
        String globalReportFile = "src/test/resources/rapport-MSC-chinguitel.txt";

        List<CDRFile> allFiles = new ArrayList<>();
        Map<String, Integer> filesPerDay = new TreeMap<>();

        // Read all daily .txt files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(inputFolder), "*.txt")) {
            for (Path dailyFile : stream) {
                String day = dailyFile.getFileName().toString().replace(".txt", "");
                List<CDRFile> dailyFiles = processDailyFile(dailyFile, day);
                allFiles.addAll(dailyFiles);
                filesPerDay.put(day, dailyFiles.size());
                System.out.println("Processed file: " + dailyFile.getFileName());
            }
        }

        // Group files by class
        Map<String, List<CDRFile>> classToFiles = new TreeMap<>();
        for (CDRFile file : allFiles) {
            classToFiles.computeIfAbsent(file.type, k -> new ArrayList<>()).add(file);
        }

        // Detect missing sequences per class
        Map<String, List<String>> classMissingRanges = new TreeMap<>();
        Map<String, Long> classMissingCount = new TreeMap<>();
        long totalMissing = 0;

        for (String type  : classToFiles.keySet()) {
            List<CDRFile> filesOfClass = classToFiles.get(type );
            List<String> missingRanges = findMissingRanges(filesOfClass);
            classMissingRanges.put(type , missingRanges);

            long missingCount = missingRanges.stream().mapToLong(CDRSequenceAnalyzer::countMissingFromRange).sum();
            classMissingCount.put(type , missingCount);
            totalMissing += missingCount;
        }

        // Write report
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(globalReportFile))) {
            writer.write("===== PER DAY ANALYSIS =====\n");
            for (String day : filesPerDay.keySet()) {
                writer.write(String.format("Date: %s | Total files: %d%n", day, filesPerDay.get(day)));
            }

            writer.write("\n===== MISSING SEQUENCES PER CLASS =====\n");
            for (String type  : classMissingRanges.keySet()) {
                List<String> missing = classMissingRanges.get(type );
                long missingCount = classMissingCount.get(type );

                writer.write("Class " + type  + ":\n");
                if (missing.isEmpty()) {
                    writer.write("  No missing sequences\n");
                } else {
                    writer.write("  Missing sequences: " + String.join(", ", missing) + "\n");
                    writer.write("  Total missing files: " + missingCount + "\n");
                }
                writer.write("\n");
            }

            long totalFiles = allFiles.size();
            writer.write("===== SUMMARY =====\n");
            writer.write(String.format("Total analysed files: %d%n", totalFiles));
            writer.write(String.format("Total missing sequences: %d%n", totalMissing));
        }

        System.out.println("Global report saved to: " + globalReportFile);
    }

    private static List<CDRFile> processDailyFile(Path dailyFile, String day) throws IOException {
        List<CDRFile> files = new ArrayList<>();
        List<String> lines = Files.readAllLines(dailyFile);
        for (String line : lines) {
            Matcher matcher = FILENAME_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String type  = matcher.group(1);
                long sequence = Long.parseLong(matcher.group(2));
                files.add(new CDRFile(type , sequence, line.trim()));
            }
        }
        return files;
    }

    private static List<String> findMissingRanges(List<CDRFile> filesOfClass) {
        List<String> ranges = new ArrayList<>();
        if (filesOfClass.isEmpty()) return ranges;

        filesOfClass.sort(Comparator.comparingLong(f -> f.sequence));

        long prev = filesOfClass.get(0).sequence;

        for (int i = 1; i < filesOfClass.size(); i++) {
            long curr = filesOfClass.get(i).sequence;
            long diff = curr - prev;

            if (diff > 1 && diff <= SEUIL) {
                ranges.add(formatRange(prev + 1, curr - 1));
            }
            prev = curr;
        }

        return ranges;
    }

    private static String formatRange(long start, long end) {
        return (start == end) ? String.valueOf(start) : start + "–" + end;
    }

    private static long countMissingFromRange(String range) {
        if (range.contains("–")) {
            String[] parts = range.split("–");
            return Long.parseLong(parts[1]) - Long.parseLong(parts[0]) + 1;
        } else {
            return 1;
        }
    }

    private static class CDRFile {
        String type;
        long sequence;
        String originalName;

        public CDRFile(String type, long sequence, String originalName) {
            this.type = type;
            this.sequence = sequence;
            this.originalName = originalName;
        }
    }
}
