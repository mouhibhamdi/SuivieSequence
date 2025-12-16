package com.advantek.suivieSequence.service.sequenceService;

import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.entity.CdrFilesSurvey;
import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import com.advantek.suivieSequence.model.DateTimeSeqObj;
import com.advantek.suivieSequence.model.GeneratorCluster;
import com.advantek.suivieSequence.repository.CdrFilesGroupIdRepo;
import com.advantek.suivieSequence.repository.StatCdrFilesRepo;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import com.advantek.suivieSequence.utils.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContinuesSequence {

    private final StatCdrFilesRepo statCdrFilesRepo;
    private final CdrFilesGroupIdRepo cdrFilesGroupIdRepo;

    public void suivieContinuesSeq(List<CdrFilesTitleConfiguration> allConfigs, LocalDate yesterday) {
        log.info("==== Start ContinuesSequence GLOBAL ====");

        LocalDate firstDate = allConfigs.stream()
                .map(SequenceListUtils::getFirstExistingDateForConfiguration)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(LocalDate::compareTo)
                .orElse(yesterday);

        LocalDate lastDate = yesterday;

        List<DateTimeSeqObj> allFilesGlobal = SequenceListUtils.loadAllFiles(allConfigs, firstDate, lastDate);

        if (allFilesGlobal.isEmpty()) {
            log.warn("No files loaded from any configuration.");
            return;
        }

        // 1) TRI GLOBAL PAR SEQUENCE uniquement
        allFilesGlobal.sort(Comparator.comparing(DateTimeSeqObj::getSequence));

        // 2) REGROUPER PAR PREFIX + NODE
        Map<String, List<DateTimeSeqObj>> prefixNodeGroups = new TreeMap<>();
        for (DateTimeSeqObj obj : allFilesGlobal) {
            String key = obj.getPrefix() + "_" + obj.getNode();
            prefixNodeGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(obj);
        }

        // 3) SPLIT EN GENERATEURS via clusters
        Map<String, Map<LocalDate, List<DateTimeSeqObj>>> filesByFinalGroup = new TreeMap<>();
        Map<LocalDate, List<GeneratorCluster>> clustersByDate = new TreeMap<>();

        for (Map.Entry<String, List<DateTimeSeqObj>> entry : prefixNodeGroups.entrySet()) {
            String baseKey = entry.getKey();
            List<DateTimeSeqObj> groupFiles = entry.getValue();

            List<GeneratorCluster> clusters = GeneratorUtils.buildClusters(groupFiles, baseKey);

            for (GeneratorCluster cluster : clusters) {
                String genKey = baseKey + "-G" + cluster.getGeneratorIndex();

                Map<LocalDate, List<DateTimeSeqObj>> byDate = new TreeMap<>();
                for (DateTimeSeqObj f : cluster.getFiles()) {
                    LocalDate day = f.getDateTime().toLocalDate();
                    byDate.computeIfAbsent(day, d -> new ArrayList<>()).add(f);

                    // Construire clustersByDate
                    clustersByDate.computeIfAbsent(day, d -> new ArrayList<>());
                    if (!clustersByDate.get(day).contains(cluster)) {
                        clustersByDate.get(day).add(cluster);
                    }
                }
                filesByFinalGroup.put(genKey, byDate);
            }
        }

        // 4) LOG MIN / MAX / TOTAL par cluster et date
        for (Map.Entry<String, Map<LocalDate, List<DateTimeSeqObj>>> entry : filesByFinalGroup.entrySet()) {
            String group = entry.getKey();
            Map<LocalDate, List<DateTimeSeqObj>> filesByDay = entry.getValue();
            for (Map.Entry<LocalDate, List<DateTimeSeqObj>> dayEntry : filesByDay.entrySet()) {
                LocalDate day = dayEntry.getKey();
                List<DateTimeSeqObj> list = dayEntry.getValue();
                int minSeq = list.stream().mapToInt(DateTimeSeqObj::getSequence).min().orElse(0);
                int maxSeq = list.stream().mapToInt(DateTimeSeqObj::getSequence).max().orElse(0);
                log.info("GROUP={} | DATE={} | MIN_SEQ={} | MAX_SEQ={} | TOTAL={}",
                        group, day, minSeq, maxSeq, list.size());
            }
        }
        // 6) CALCUL ET SAUVEGARDE DES SEQUENCES MANQUANTES
        calculateAndSaveMissingSequences(filesByFinalGroup);

        // 7) GENERATION FICHIER TXT
        generateCdrSortedTxt(filesByFinalGroup);

        log.info("==== ContinuesSequence GLOBAL finished ====");
    }

    private void generateCdrSortedTxt(Map<String, Map<LocalDate, List<DateTimeSeqObj>>> filesByGroup) {
        String outputPath = "src/test/resources/cdr_files_sorted_by_day_all_nodes.txt";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("===== CDR FILES SORTED BY DAY, NODE AND GENERATOR =====\n\n");

            for (Map.Entry<String, Map<LocalDate, List<DateTimeSeqObj>>> entry : filesByGroup.entrySet()) {
                String group = entry.getKey();
                Map<LocalDate, List<DateTimeSeqObj>> filesByDay = entry.getValue();
                writer.write("==== GROUP: " + group + " ====\n\n");

                for (Map.Entry<LocalDate, List<DateTimeSeqObj>> dayEntry : filesByDay.entrySet()) {
                    LocalDate day = dayEntry.getKey();
                    List<DateTimeSeqObj> list = dayEntry.getValue();

                    int minSeq = list.stream().mapToInt(DateTimeSeqObj::getSequence).min().orElse(0);
                    int maxSeq = list.stream().mapToInt(DateTimeSeqObj::getSequence).max().orElse(0);

                    writer.write(String.format(
                            "=== Date: %s | MIN=%d | MAX=%d | TOTAL=%d ===\n",
                            day, minSeq, maxSeq, list.size()));

                    list.sort(Comparator.comparing(DateTimeSeqObj::getSequence));

                    for (DateTimeSeqObj f : list) {
                        writer.write(String.format(
                                "%s | Seq:%d | Node:%s | Gen:%d\n",
                                f.getDateTime().format(dtf),
                                f.getSequence(),
                                f.getNode(),
                                f.getGeneratorId()));
                    }
                    writer.write("\n---------------------------------------\n\n");
                }
            }

        } catch (IOException e) {
            log.error("Error writing TXT file: {}", e.getMessage());
        }
    }

    /**
     * Calcule les séquences manquantes pour chaque group-générateur-jour
     * et sauveg arde dans CdrFilesSurvey et CdrFilesGroupId
     */
    private void calculateAndSaveMissingSequences(Map<String, Map<LocalDate, List<DateTimeSeqObj>>> filesByGroup) {
        log.info("=== Calcul des séquences manquantes ===");

        for (Map.Entry<String, Map<LocalDate, List<DateTimeSeqObj>>> groupEntry : filesByGroup.entrySet()) {
            String groupKey = groupEntry.getKey(); // Ex: "ACIPGWb_003-G1"
            Map<LocalDate, List<DateTimeSeqObj>> filesByDay = groupEntry.getValue();

            // Parser le groupKey pour extraire les informations
            String[] parts = groupKey.split("-G");
            String baseGroup = parts[0]; // "ACIPGWb_003"
            int generatorId = Integer.parseInt(parts[1]); // "1"

            String[] baseParts = baseGroup.split("_");
            String prefix = baseParts[0]; // "ACIPGWb"
            String node = baseParts[1]; // "003"

            // Pour chaque jour
            for (Map.Entry<LocalDate, List<DateTimeSeqObj>> dayEntry : filesByDay.entrySet()) {
                LocalDate date = dayEntry.getKey();
                List<DateTimeSeqObj> files = dayEntry.getValue();

                if (files.isEmpty())
                    continue;

                // Trier par séquence
                files.sort(Comparator.comparingInt(DateTimeSeqObj::getSequence));

                int minSeq = files.get(0).getSequence();
                int maxSeq = files.get(files.size() - 1).getSequence();

                // Calculer les séquences manquantes (gère les duplicatas)
                List<Integer> missingSequences = calculateMissingSequencesList(files, minSeq, maxSeq);

                // Compter les fichiers DISTINCTS (pas de duplicatas)
                Set<Integer> distinctSequences = new HashSet<>();
                for (DateTimeSeqObj file : files) {
                    distinctSequences.add(file.getSequence());
                }
                int totalDistinctFiles = distinctSequences.size();
                int totalFiles = files.size();
                int duplicateCount = totalFiles - totalDistinctFiles;

                int expectedFiles = maxSeq - minSeq + 1;
                int missingCount = missingSequences.size();

                if (duplicateCount > 0) {
                    log.warn("GROUP={} | DATE={} | DUPLICATAS DÉTECTÉS: {} fichiers avec séquences identiques",
                            groupKey, date, duplicateCount);
                }

                log.info("GROUP={} | DATE={} | MIN={} | MAX={} | REÇUS={} | DISTINCTS={} | ATTENDUS={} | MANQUANTS={}",
                        groupKey, date, minSeq, maxSeq, totalFiles, totalDistinctFiles, expectedFiles, missingCount);

                // Sauvegarder dans CdrFilesSurvey (utiliser les fichiers DISTINCTS)
                saveToCdrFilesSurvey(date, prefix, node, generatorId, totalDistinctFiles, missingCount);

                // Sauvegarder dans CdrFilesGroupId
                saveToCdrFilesGroupId(date, prefix, groupKey, node, generatorId,
                        minSeq, maxSeq, missingSequences, files);
            }
        }

        log.info("=== Fin calcul séquences manquantes ===");
    }

    /**
     * Calcule la liste des séquences manquantes entre min et max
     */
    private List<Integer> calculateMissingSequencesList(List<DateTimeSeqObj> files, int minSeq, int maxSeq) {
        List<Integer> missing = new ArrayList<>();
        Set<Integer> receivedSeqs = new HashSet<>();

        // Créer un Set des séquences reçues
        for (DateTimeSeqObj file : files) {
            receivedSeqs.add(file.getSequence());
        }

        // Trouver les manquants entre min et max
        for (int seq = minSeq; seq <= maxSeq; seq++) {
            if (!receivedSeqs.contains(seq)) {
                missing.add(seq);
            }
        }

        return missing;
    }

    /**
     * Sauvegarde l'état journalier dans CdrFilesSurvey
     */
    private void saveToCdrFilesSurvey(LocalDate date, String prefix, String node,
            int generatorId, int count, int missing) {
        try {
            // Format du type: "prefix_node_Gx" Ex: "ACIPGWb_003_G1"
            String type = prefix + "_" + node + "_G" + generatorId;

            // Chercher l'enregistrement existant
            Optional<CdrFilesSurvey> existingOpt = statCdrFilesRepo.findByDateAndTypeAndNode(date, type, node);

            CdrFilesSurvey survey;
            if (existingOpt.isPresent()) {
                survey = existingOpt.get();
                survey.setCount(BigDecimal.valueOf(count));
                survey.setMissing(BigDecimal.valueOf(missing));
            } else {
                survey = new CdrFilesSurvey();
                survey.setDate(date);
                survey.setType(type);
                survey.setNode(node);
                survey.setCount(BigDecimal.valueOf(count));
                survey.setMissing(BigDecimal.valueOf(missing));
            }

            statCdrFilesRepo.save(survey);
            log.debug("CdrFilesSurvey saved: date={}, type={}, count={}, missing={}",
                    date, type, count, missing);

        } catch (Exception e) {
            log.error("Error saving CdrFilesSurvey: {}", e.getMessage(), e);
        }
    }

    /**
     * Sauvegarde les informations du group dans CdrFilesGroupId
     */
    private void saveToCdrFilesGroupId(LocalDate date, String prefix, String groupKey, String node,
            int generatorId, int minSeq, int maxSeq,
            List<Integer> missingSequences, List<DateTimeSeqObj> files) {
        try {
            String cdrType = prefix;
            String cdrGroup = groupKey; // Ex: "ACIPGWb_003-G1"
            String category = "G" + generatorId;

            // Chercher l'enregistrement existant
            Optional<CdrFilesGroupId> existingOpt = cdrFilesGroupIdRepo
                    .findByCdrTypeAndCdrGroupAndCategoryAndNoeudAndDate(
                            cdrType, cdrGroup, category, node, date);

            CdrFilesGroupId groupId;
            if (existingOpt.isPresent()) {
                groupId = existingOpt.get();
                groupId.setMin(minSeq);
                groupId.setMax(maxSeq);
                groupId.setUnreceivedSeqSet(missingSequences);
                // Mettre à jour maxDateTime avec le dernier fichier
                if (!files.isEmpty()) {
                    groupId.setMaxDateTime(files.get(files.size() - 1).getDateTime());
                }
            } else {
                groupId = new CdrFilesGroupId(cdrType, cdrGroup, category, node, date, minSeq);
                groupId.setMax(maxSeq);
                groupId.setUnreceivedSeqSet(missingSequences);
                if (!files.isEmpty()) {
                    groupId.setMaxDateTime(files.get(files.size() - 1).getDateTime());
                }
            }

            cdrFilesGroupIdRepo.save(groupId);
            log.debug("CdrFilesGroupId saved: date={}, group={}, min={}, max={}, missing={}",
                    date, cdrGroup, minSeq, maxSeq, missingSequences.size());

        } catch (Exception e) {
            log.error("Error saving CdrFilesGroupId: {}", e.getMessage(), e);
        }
    }
}
