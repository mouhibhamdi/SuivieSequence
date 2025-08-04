package com.advantek.suivieSequence.service.sequenceService;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CdrIncrementalIdAnalyzer {

    private static final Pattern FILE_PATTERN = Pattern.compile("^(SITE1_MSC\\dPGWCDR)(\\d{4})(\\d{4})\\.dat\\.gz$");

    public static void main(String[] args) {
        File inputFile = new File("src/test/resources/2025-01-03.txt");

        // 🔽 Extraction de la date à partir du nom du fichier (sans l’extension)
        String inputFileName = inputFile.getName();
        String fileDate = inputFileName.replace(".txt", "");  // Ex: "2025-01-02"

        // 🔽 Création du nom du rapport avec la date
        File outputReport = new File("src/test/resources/cdr-incremental-report-" + fileDate + ".txt");

        CdrIncrementalIdAnalyzer analyzer = new CdrIncrementalIdAnalyzer();

        try {
            analyzer.analyze(inputFile, outputReport);
            System.out.println("✅ Analyse terminée. Rapport généré dans : " + outputReport.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'analyse : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void analyze(File txtInputFile, File outputReportFile) throws IOException {
        Map<String, List<FileEntry>> groupedByType = new HashMap<>();

        for (String line : Files.readAllLines(txtInputFile.toPath())) {
            Matcher matcher = FILE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String type = matcher.group(1);
                int id = Integer.parseInt(matcher.group(2));
                int seq = Integer.parseInt(matcher.group(3));

                groupedByType.computeIfAbsent(type, k -> new ArrayList<>())
                        .add(new FileEntry(line.trim(), type, id, seq));
            }
        }

        try (PrintWriter writer = new PrintWriter(outputReportFile)) {
            int totalMissing = 0;

            for (String type : groupedByType.keySet()) {
                writer.println("Type: " + type);
                List<FileEntry> entries = groupedByType.get(type);
                entries.sort(Comparator.comparingInt(FileEntry::getCombinedIndex));

                int prevId = -1, prevSeq = -1;
                for (FileEntry entry : entries) {
                    if (prevId == -1) {
                        prevId = entry.id;
                        prevSeq = entry.seq;
                        continue;
                    }

                    int expectedCombined = prevId * 10000 + prevSeq + 1;
                    int currentCombined = entry.getCombinedIndex();

                    if (currentCombined > expectedCombined) {
                        int missingCount = currentCombined - expectedCombined;
                        totalMissing += missingCount;
                        for (int i = expectedCombined; i < currentCombined; i++) {
                            int missingId = i / 10000;
                            int missingSeq = i % 10000;
                            writer.printf("  ➤ Missing: %s%04d%04d.dat.gz%n", type, missingId, missingSeq);
                        }
                    }

                    prevId = entry.id;
                    prevSeq = entry.seq;
                }

                writer.println();
            }

            writer.println("📊 Total estimé de fichiers manquants : " + totalMissing);
        }
    }

    private static class FileEntry {
        String name;
        String type;
        int id;
        int seq;

        public FileEntry(String name, String type, int id, int seq) {
            this.name = name;
            this.type = type;
            this.id = id;
            this.seq = seq;
        }

        public int getCombinedIndex() {
            return id * 10000 + seq;
        }
    }
}
