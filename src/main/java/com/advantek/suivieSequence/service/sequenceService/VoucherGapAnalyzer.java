package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

public class VoucherGapAnalyzer {

    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "(vou_\\d+_\\d+_\\d+)_?(\\d{14})_(\\d+)\\.unl\\.gz"
    );
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    static class VoucherEntry {
        String filename;
        String typeId;
        LocalDate realDate;
        long sequence;

        VoucherEntry(String filename, String typeId, LocalDate realDate, long sequence) {
            this.filename = filename;
            this.typeId = typeId;
            this.realDate = realDate;
            this.sequence = sequence;
        }
    }

    public static void main(String[] args) throws IOException {
        Path folder = Paths.get("src/test/resources/IN/");
        Path reportPath = folder.resolve("voucher-report-global.txt");
        try (BufferedWriter reportWriter = Files.newBufferedWriter(reportPath)) {
            reportWriter.write("📊 Rapport Global Analyse Voucher (par séquence stricte)\n\n");

            Map<LocalDate, Map<String, List<VoucherEntry>>> allData = new TreeMap<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.txt")) {
                for (Path file : stream) {
                    String filename = file.getFileName().toString();
                    // Ignore carryover et rapport
                    if (filename.startsWith("voucher-carryover-") || filename.equals("voucher-report-global.txt")) {
                        continue;
                    }
                    processFile(file, allData, folder);
                }
            }

            int globalTotalFiles = 0;
            int globalMissingFiles = 0;

            StringBuilder reportByDateBuilder = new StringBuilder();

            for (Map.Entry<LocalDate, Map<String, List<VoucherEntry>>> dateEntry : allData.entrySet()) {
                LocalDate date = dateEntry.getKey();
                Map<String, List<VoucherEntry>> typeMap = dateEntry.getValue();

                int totalFilesForDate = 0;
                int totalMissingForDate = 0;

                reportByDateBuilder.append("📅 Date analysée : ").append(date).append("\n");
                reportByDateBuilder.append("──────────────────────────────────────────────\n");

                for (Map.Entry<String, List<VoucherEntry>> typeEntry : typeMap.entrySet()) {
                    String type = typeEntry.getKey();
                    List<VoucherEntry> entries = typeEntry.getValue();
                    entries.sort(Comparator.comparingLong(e -> e.sequence));

                    int total = entries.size();
                    int missing = 0;

                    for (int i = 1; i < entries.size(); i++) {
                        long prev = entries.get(i - 1).sequence;
                        long curr = entries.get(i).sequence;

                        if (curr < prev) {
                            // Reset séquence détecté, on ignore ce gap
                            continue;
                        }

                        long gap = curr - prev;
                        if (gap > 1) {
                            missing += (int) (gap - 1);
                        }
                    }

                    reportByDateBuilder.append("🔸 Type : ").append(type).append("\n");
                    reportByDateBuilder.append("   - Fichiers présents : ").append(total).append("\n");
                    reportByDateBuilder.append("   - Fichiers manquants estimés : ").append(missing).append("\n\n");

                    totalFilesForDate += total;
                    totalMissingForDate += missing;
                }

                reportByDateBuilder.append("📌 Résumé Date : ").append(date).append("\n");
                reportByDateBuilder.append("   - Total fichiers analysés : ").append(totalFilesForDate).append("\n");
                reportByDateBuilder.append("   - Total fichiers manquants estimés : ").append(totalMissingForDate).append("\n");
                reportByDateBuilder.append("══════════════════════════════════════════════════\n\n");

                globalTotalFiles += totalFilesForDate;
                globalMissingFiles += totalMissingForDate;
            }

            reportWriter.write("🧾 Résumé Global Final\n");
            reportWriter.write("==================================================\n");
            reportWriter.write("📦 Total fichiers analysés : " + globalTotalFiles + "\n");
            reportWriter.write("❌ Total fichiers manquants estimés : " + globalMissingFiles + "\n");
            reportWriter.write("==================================================\n\n");

            reportWriter.write(reportByDateBuilder.toString());
        }

        System.out.println("✅ Rapport global généré.");
    }

    private static void processFile(Path file, Map<LocalDate, Map<String, List<VoucherEntry>>> allData, Path folder) throws IOException {
        String fileName = file.getFileName().toString().replace(".txt", "");
        LocalDate fileDate;
        try {
            fileDate = LocalDate.parse(fileName);
        } catch (DateTimeParseException e) {
            System.err.println("⛔ Fichier ignoré (non daté) : " + fileName);
            return;
        }

        System.out.println("▶️ Analyse du fichier voucher : " + file.getFileName());

        List<String> lines = new ArrayList<>();

        // Chargement carryover si existant (dans le même dossier)
        Path carryIn = folder.resolve("voucher-carryover-" + fileDate + ".txt");
        if (Files.exists(carryIn)) {
            System.out.println("   📥 Chargement du fichier carryover associé : " + carryIn.getFileName());
            lines.addAll(Files.readAllLines(carryIn));
            Files.delete(carryIn);  // supprimer si tu veux garder, commente cette ligne
        }

        // Chargement du fichier principal
        lines.addAll(Files.readAllLines(file));

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher matcher = FILENAME_PATTERN.matcher(trimmed);
            if (!matcher.matches()) continue;

            String type = matcher.group(1);
            String tsStr = matcher.group(2);
            String seqStr = matcher.group(3);

            try {
                LocalDateTime ts = LocalDateTime.parse(tsStr, TIMESTAMP_FORMATTER);
                long sequence = Long.parseLong(seqStr);
                LocalDate realDate = ts.toLocalDate();

                VoucherEntry entry = new VoucherEntry(trimmed, type, realDate, sequence);

                if (!realDate.equals(fileDate)) {
                    // Fichier hors date, on le déplace dans le carryover de sa vraie date
                    Path carryTarget = folder.resolve("voucher-carryover-" + realDate + ".txt");
                    Files.write(carryTarget, List.of(trimmed), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    continue;
                }

                allData.computeIfAbsent(realDate, d -> new HashMap<>())
                        .computeIfAbsent(type, t -> new ArrayList<>())
                        .add(entry);
            } catch (Exception e) {
                System.err.println("❌ Erreur parsing : " + trimmed);
            }
        }
    }
}
