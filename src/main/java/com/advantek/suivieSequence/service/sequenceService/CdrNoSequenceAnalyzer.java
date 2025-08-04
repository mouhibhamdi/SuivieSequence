package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class CdrNoSequenceAnalyzer {

    static class CdrPatternConfig {
        Pattern pattern;
        DateTimeFormatter formatter;

        public CdrPatternConfig(String regex, String dateFormat) {
            this.pattern = Pattern.compile(regex);
            this.formatter = DateTimeFormatter.ofPattern(dateFormat);
        }
    }

    static class TimeGroup {
        String name;
        int startHour;
        int endHour;
        List<LocalDateTime> timestamps = new ArrayList<>();
        List<String> filenames = new ArrayList<>();

        public TimeGroup(String name, int startHour, int endHour) {
            this.name = name;
            this.startHour = startHour;
            this.endHour = endHour;
        }

        boolean isInGroup(LocalDateTime dt) {
            int hour = dt.getHour();
            return startHour < endHour ? (hour >= startHour && hour < endHour) : (hour >= startHour || hour < endHour);
        }
    }

    private static final Map<String, CdrPatternConfig> CONFIGS = new HashMap<>();

    static {
        CONFIGS.put("bHWMSC1", new CdrPatternConfig("bHWMSC1(\\d{8})(\\d{8})\\.dat\\.gz", "yyyyMMddHHmmssSS"));
        CONFIGS.put("bHWMSC2", new CdrPatternConfig("bHWMSC2(\\d{8})(\\d{8})\\.dat\\.gz", "yyyyMMddHHmmssSS"));
        CONFIGS.put("bHWMSC3", new CdrPatternConfig("bHWMSC3(\\d{8})(\\d{8})\\.dat\\.gz", "yyyyMMddHHmmssSS"));
        CONFIGS.put("bSNSMSC1", new CdrPatternConfig("bSNSMSC1(\\d{8})(\\d{8})\\.dat\\.gz", "yyyyMMddHHmmssSS"));
    }

    public static void main(String[] args) throws IOException {
        Path inputFile = Paths.get("src/test/resources/2025-01-02.txt");
        String inputFileName = inputFile.getFileName().toString().replace(".txt", "");
        LocalDate currentDate = LocalDate.parse(inputFileName); // Extract the date from file name

        List<String> allLines = new ArrayList<>(Files.readAllLines(inputFile));
        System.out.println("🔍 Fichiers initiaux dans " + inputFile.getFileName() + " : " + allLines.size());

        // Try to load carryover file for the same day
        Path carryoverFile = Paths.get("src/test/resources/carryover-" + inputFileName + ".txt");
        if (Files.exists(carryoverFile)) {
            List<String> carryLines = Files.readAllLines(carryoverFile);
            System.out.println("📦 Fichiers carryover trouvés : " + carryLines.size());

            allLines.addAll(carryLines);
            Files.delete(carryoverFile);
            System.out.println("🧹 Carryover supprimé : " + carryoverFile.getFileName());
        }

        System.out.println("📊 Total brut après merge : " + allLines.size());

        Map<String, List<String>> groupedByType = new HashMap<>();
        Set<String> uniqueValidFiles = new HashSet<>();
        int acceptedCount = 0;
        int skippedCount = 0;

        for (String line : allLines) {
            String filename = line.trim();

            for (Map.Entry<String, CdrPatternConfig> entry : CONFIGS.entrySet()) {
                String prefix = entry.getKey();
                CdrPatternConfig config = entry.getValue();

                if (filename.startsWith(prefix)) {
                    Matcher matcher = config.pattern.matcher(filename);
                    if (matcher.matches()) {
                        String ts = matcher.group(1) + matcher.group(2);
                        try {
                            LocalDateTime dt = LocalDateTime.parse(ts, config.formatter);
                            if (dt.toLocalDate().equals(currentDate)) {
                                if (uniqueValidFiles.add(filename)) {
                                    groupedByType.computeIfAbsent(prefix, k -> new ArrayList<>()).add(filename);
                                    acceptedCount++;
                                } else {
                                    skippedCount++;
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Timestamp invalide dans : " + filename);
                        }
                    }
                    break;
                }
            }
        }

        System.out.println("✅ Fichiers valides pour la date " + currentDate + " : " + acceptedCount);
        System.out.println("⛔ Fichiers ignorés ou dupliqués : " + skippedCount);

        for (Map.Entry<String, List<String>> entry : groupedByType.entrySet()) {
            String prefix = entry.getKey();
            List<String> filenames = entry.getValue();
            CdrPatternConfig config = CONFIGS.get(prefix);

            if (config != null) {
                Path outputFile = Paths.get("src/test/resources/report-" + prefix + "-" + inputFileName + ".txt");
                analyzeFilesByTimeGroups(filenames, config, outputFile);
            }
        }
    }

    private static void analyzeFilesByTimeGroups(List<String> filenames, CdrPatternConfig config, Path outputFile) throws IOException {
        List<TimeGroup> groups = Arrays.asList(
                new TimeGroup("00h-04h", 0, 4),
                new TimeGroup("04h-12h", 4, 12),
                new TimeGroup("12h-16h", 12, 16),
                new TimeGroup("16h-23h", 16, 23),
                new TimeGroup("23h-00h", 23, 0)
        );

        for (String filename : filenames) {
            Matcher matcher = config.pattern.matcher(filename);
            if (matcher.matches()) {
                String ts = matcher.group(1) + matcher.group(2);
                try {
                    LocalDateTime dt = LocalDateTime.parse(ts, config.formatter);
                    for (TimeGroup group : groups) {
                        if (group.isInGroup(dt)) {
                            group.timestamps.add(dt);
                            group.filenames.add(filename);
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ Invalid timestamp in: " + filename);
                }
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write("📊 Rapport par groupes horaires (IQR)\n");
            writer.write("----------------------------------------\n");

            int totalFiles = 0, totalAnomalies = 0, totalMissing = 0;

            for (TimeGroup group : groups) {
                if (group.timestamps.size() < 2) {
                    writer.write("📌 Groupe " + group.name + " : Pas assez de données (" + group.timestamps.size() + " fichiers)\n\n");
                    continue;
                }

                totalFiles += group.timestamps.size();

                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < group.timestamps.size(); i++) order.add(i);
                order.sort(Comparator.comparing(group.timestamps::get));

                List<Double> gaps = new ArrayList<>();
                for (int i = 1; i < order.size(); i++) {
                    int prev = order.get(i - 1);
                    int curr = order.get(i);
                    double gap = Duration.between(group.timestamps.get(prev), group.timestamps.get(curr)).toMillis() / 1000.0;
                    gaps.add(gap);
                }

                List<Double> sortedGaps = new ArrayList<>(gaps);
                Collections.sort(sortedGaps);
                double q1 = sortedGaps.get(sortedGaps.size() / 4);
                double q3 = sortedGaps.get((sortedGaps.size() * 3) / 4);
                double iqr = q3 - q1;
                double threshold = q3 + 1.5 * iqr;
                double medianGap = sortedGaps.get(sortedGaps.size() / 2);

                writer.write("📌 Groupe " + group.name + " (" + group.timestamps.size() + " fichiers)\n");
                writer.write(String.format("    Q1 : %.2f sec\n", q1));
                writer.write(String.format("    Q3 : %.2f sec\n", q3));
                writer.write(String.format("    IQR : %.2f sec\n", iqr));
                writer.write(String.format("    Seuil (Q3 + 1.5×IQR) : %.2f sec\n", threshold));

                int anomalies = 0, missing = 0;

                for (int i = 1; i < order.size(); i++) {
                    int prev = order.get(i - 1);
                    int curr = order.get(i);
                    double gap = Duration.between(group.timestamps.get(prev), group.timestamps.get(curr)).toMillis() / 1000.0;

                    if (group.startHour == 23 && gap > 20000) continue;

                    if (gap > threshold) {
                        int estimated = Math.max(0, (int) Math.round(gap / medianGap) - 1);
                        missing += estimated;
                        anomalies++;

                        writer.write(String.format("       ⚠️ Gap %.2f sec entre :\n", gap));
                        writer.write("          • " + group.filenames.get(prev) + "\n");
                        writer.write("          • " + group.filenames.get(curr) + "\n");
                        writer.write("          🔻 Estimation fichiers manquants : " + estimated + "\n");
                    }
                }

                totalAnomalies += anomalies;
                totalMissing += missing;

                writer.write("    ➤ Anomalies : " + anomalies + "\n");
                writer.write("    ➤ Fichiers manquants estimés : " + missing + "\n\n");
            }

            writer.write("========================================\n");
            writer.write("📊 Résumé global\n");
            writer.write("----------------------------------------\n");
            writer.write("Total fichiers analysés : " + totalFiles + "\n");
            writer.write("Total anomalies : " + totalAnomalies + "\n");
            writer.write("Total fichiers manquants estimés : " + totalMissing + "\n");
        }

        System.out.println("✅ Rapport généré : " + outputFile.getFileName());
    }
}
