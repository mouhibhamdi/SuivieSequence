package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class SharedSequenceGapAnalyzer {

    private static final Pattern FILENAME_PATTERN = Pattern.compile("([A-Z]+PGWb\\d{4})(\\d{4})(\\d{14})\\.dat\\.gz");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    static class CdrEntry {
        String filename;
        String subType;
        LocalDateTime timestamp;
        int sequence;

        CdrEntry(String filename, String subType, LocalDateTime timestamp, int sequence) {
            this.filename = filename;
            this.subType = subType;
            this.timestamp = timestamp;
            this.sequence = sequence;
        }
    }

    public static void main(String[] args) throws IOException {
        Path inputFile = Paths.get("src/test/resources/2025-01-10.txt");
        String inputFileName = inputFile.getFileName().toString().replace(".txt", "");
        LocalDate inputDate = LocalDate.parse(inputFileName);

        Set<LocalDate> acceptedDates = Set.of(
                inputDate.minusDays(2),
                inputDate.minusDays(1),
                inputDate
        );

        // 🔁 Charger le carryover lié à la date analysée (produit par analyse d'une date ultérieure)
        Path carryPath = Paths.get("src/test/resources/carryover-" + inputDate + ".txt");
        List<String> lines = new ArrayList<>();
        if (Files.exists(carryPath)) {
            lines.addAll(Files.readAllLines(carryPath));
            System.out.println("✔️ Fichiers importés depuis le carryover : " + carryPath.getFileName());
            Files.delete(carryPath); // Supprimer après utilisation
        }

        // 📥 Ajouter les lignes du fichier .txt courant
        lines.addAll(Files.readAllLines(inputFile));

        List<CdrEntry> entries = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher matcher = FILENAME_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                String subType = matcher.group(1);
                String seqStr = matcher.group(2);
                String dateStr = matcher.group(3);
                try {
                    LocalDateTime ts = LocalDateTime.parse(dateStr, DATE_FORMATTER);
                    LocalDate realDate = ts.toLocalDate();
                    int seq = Integer.parseInt(seqStr);

                    // ✅ Ajout aux fichiers analysés si la date réelle fait partie de la plage
                    if (acceptedDates.contains(realDate)) {
                        entries.add(new CdrEntry(trimmed, subType, ts, seq));
                    }

                    // 📤 Enregistrement dans le carryover si la date réelle est avant la date analysée
                    if (realDate.isBefore(inputDate)) {
                        Path carryTarget = Paths.get("src/test/resources/carryover-" + realDate + ".txt");
                        Files.createDirectories(carryTarget.getParent());
                        Files.write(carryTarget, List.of(trimmed), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }

                } catch (Exception e) {
                    System.err.println("❌ Erreur parsing : " + trimmed + " → " + e.getMessage());
                }
            } else {
                System.err.println("❌ Regex ne correspond pas : " + trimmed);
            }
        }

        // 🔄 Groupement par sous-type complet
        Map<String, List<CdrEntry>> grouped = new HashMap<>();
        for (CdrEntry entry : entries) {
            grouped.computeIfAbsent(entry.subType, k -> new ArrayList<>()).add(entry);
        }

        // Variables pour résumé détaillé
        Map<String, Integer> anomaliesParSousType = new HashMap<>();
        Map<String, Integer> manquantsParSousType = new HashMap<>();
        Map<String, Integer> fichiersParSousType = new HashMap<>();

        Path reportFile = Paths.get("src/test/resources/cdr-shared-sequence-report-" + inputDate + ".txt");
        Files.createDirectories(reportFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(reportFile)) {
            writer.write("📊 Rapport Anomalies CDR par Sous-Type complet\n");
            writer.write("Date analysée : " + inputDate + "\n");
            writer.write("Fenêtre glissante : " + inputDate.minusDays(2) + " → " + inputDate + "\n\n");

            int totalMissing = 0;
            int totalAnomalies = 0;

            for (Map.Entry<String, List<CdrEntry>> entry : grouped.entrySet()) {
                String subType = entry.getKey();
                List<CdrEntry> groupEntries = entry.getValue();

                // ✅ Tri par séquence PUIS timestamp
                groupEntries.sort(
                        Comparator.comparingInt((CdrEntry e) -> e.sequence)
                                .thenComparing(e -> e.timestamp)
                );

                // // 📝 Export du tri pour vérification (désactivé)
                // Path sortedFile = Paths.get("src/test/resources/sorted-" + subType + "-" + inputDate + ".txt");
                // try (BufferedWriter sortWriter = Files.newBufferedWriter(sortedFile)) {
                //     for (CdrEntry cdr : groupEntries) {
                //         sortWriter.write(String.format(
                //                 "SEQ=%04d | %s | %s\n",
                //                 cdr.sequence,
                //                 cdr.timestamp.format(DATE_FORMATTER),
                //                 cdr.filename
                //         ));
                //     }
                // }

                int anomalyCount = 0;
                int missingCount = 0;

                for (int i = 1; i < groupEntries.size(); i++) {
                    int prev = groupEntries.get(i - 1).sequence;
                    int curr = groupEntries.get(i).sequence;
                    int gap = curr - prev;
                    if (gap < 0) {
                        gap = curr + (9999 - prev) + 1;
                    }

                    if (gap > 1) {
                        int missing = gap - 1;
                        missingCount += missing;
                        anomalyCount++;

                        writer.write("   ⚠️ Gap de " + gap + " entre :\n");
                        writer.write("      • " + groupEntries.get(i - 1).filename + "\n");
                        writer.write("      • " + groupEntries.get(i).filename + "\n");
                        writer.write("      🔻 Estimation fichiers manquants : " + missing + "\n");
                    }
                }

                writer.write("🔹 Sous-Type : " + subType + "\n");
                writer.write("   Fichiers analysés : " + groupEntries.size() + "\n");
                writer.write("   ➤ Total anomalies : " + anomalyCount + "\n");
                writer.write("   ➤ Fichiers manquants estimés : " + missingCount + "\n\n");

                anomaliesParSousType.put(subType, anomalyCount);
                manquantsParSousType.put(subType, missingCount);
                fichiersParSousType.put(subType, groupEntries.size());

                totalMissing += missingCount;
                totalAnomalies += anomalyCount;
            }

            writer.write("📌 Résumé global\n");
            writer.write("------------------------\n");
            writer.write("Total anomalies détectées : " + totalAnomalies + "\n");
            writer.write("Total fichiers manquants estimés : " + totalMissing + "\n\n");
        }

        System.out.println("✅ Rapport généré : " + reportFile.getFileName());
    }
}
