package com.advantek.suivieSequence.utils;

import com.advantek.suivieSequence.model.DateTimeSeqObj;
import com.advantek.suivieSequence.model.GeneratorCluster;

import java.util.*;

public class GeneratorUtils {

    private static final int SEQUENCE_MAX = 99999;
    // Seuil pour détecter un saut arrière (changement de générateur)
    private static final int BACKWARD_JUMP_THRESHOLD = 100;
    /**
     * Détecte les générateurs en analysant l'alternance temporelle des séquences.
     * Deux générateurs qui alternent auront des "sauts arrière" dans l'ordre
     * temporel.
     */
    public static List<GeneratorCluster> buildClusters(List<DateTimeSeqObj> sortedFiles, String baseKey) {
        if (sortedFiles == null || sortedFiles.isEmpty()) {
            return new ArrayList<>();
        }

        System.out.println("\n=== Détection de générateurs par alternance temporelle pour " + baseKey + " ===");
        System.out.println("Total fichiers: " + sortedFiles.size());

        // Étape 1: Trier par datetime pour analyser l'ordre temporel
        sortedFiles.sort(Comparator.comparing(DateTimeSeqObj::getDateTime));

        // Étape 2: Assigner chaque fichier à un générateur basé sur l'alternance
        assignGeneratorsByAlternation(sortedFiles);

        // Étape 3: Construire les clusters finaux
        List<GeneratorCluster> clusters = buildClustersFromAssignments(sortedFiles, baseKey);

        // Afficher le résumé
        printDetectionSummary(clusters);

        return clusters;
    }

    /**
     * Assigne les fichiers aux générateurs en détectant les alternances
     * temporelles.
     * Principe : Si la séquence recule significativement, c'est un autre générateur.
     */
    private static void assignGeneratorsByAlternation(List<DateTimeSeqObj> files) {
        if (files.isEmpty())
            return;

        // Map : GeneratorId → dernière séquence vue pour ce générateur
        Map<Integer, Integer> generatorLastSeq = new HashMap<>();
        int nextGeneratorId = 1;

        for (DateTimeSeqObj file : files) {
            int currentSeq = file.getSequence();
            Integer assignedGen = findBestMatchingGenerator(currentSeq, generatorLastSeq);

            if (assignedGen == null) {
                // Créer un nouveau générateur
                assignedGen = nextGeneratorId++;
                generatorLastSeq.put(assignedGen, currentSeq);
            } else {
                // Mettre à jour la dernière séquence de ce générateur
                generatorLastSeq.put(assignedGen, currentSeq);
            }

            file.setGeneratorId(assignedGen);
        }

        System.out.println("Nombre de générateurs détectés: " + generatorLastSeq.size());
    }

    /**
     * Trouve le générateur qui correspond le mieux à cette séquence.
     * Un générateur correspond si la séquence continue logiquement (pas de saut
     * arrière).
     */
    private static Integer findBestMatchingGenerator(int currentSeq, Map<Integer, Integer> generatorLastSeq) {
        Integer bestGen = null;
        int smallestForwardGap = Integer.MAX_VALUE;

        for (Map.Entry<Integer, Integer> entry : generatorLastSeq.entrySet()) {
            int genId = entry.getKey();
            int lastSeq = entry.getValue();

            // Calculer le gap (avec support du wrap-around)
            int gap = calculateSequenceGap(lastSeq, currentSeq);

            // Si le gap est positif et petit, c'est probablement le même générateur
            if (gap >= 0 && gap < BACKWARD_JUMP_THRESHOLD) {
                if (gap < smallestForwardGap) {
                    smallestForwardGap = gap;
                    bestGen = genId;
                }
            }
        }

        return bestGen;
    }

    /**
     * Calcule le gap entre deux séquences avec support du wrap-around.
     * Retourne un gap négatif si c'est un saut arrière significatif.
     */
    private static int calculateSequenceGap(int fromSeq, int toSeq) {
        if (toSeq >= fromSeq) {
            // Progression normale
            return toSeq - fromSeq;
        } else {
            // Possible wrap-around ou saut arrière
            int wrapGap = (SEQUENCE_MAX + 1) - fromSeq + toSeq;

            // Si le wrap gap est raisonnable (< 10000), c'est un wrap-around
            if (wrapGap < 10000) {
                return wrapGap;
            } else {
                // C'est un vrai saut arrière (changement de générateur)
                return toSeq - fromSeq; // Retourne un nombre négatif
            }
        }
    }

    /**
     * Construit les clusters à partir des IDs de générateurs assignés.
     */
    private static List<GeneratorCluster> buildClustersFromAssignments(List<DateTimeSeqObj> files, String baseKey) {
        // Grouper les fichiers par generatorId
        Map<Integer, List<DateTimeSeqObj>> filesByGen = new TreeMap<>();
        for (DateTimeSeqObj file : files) {
            filesByGen.computeIfAbsent(file.getGeneratorId(), k -> new ArrayList<>()).add(file);
        }

        // Créer les clusters
        List<GeneratorCluster> clusters = new ArrayList<>();
        for (Map.Entry<Integer, List<DateTimeSeqObj>> entry : filesByGen.entrySet()) {
            int genId = entry.getKey();
            List<DateTimeSeqObj> genFiles = entry.getValue();

            GeneratorCluster cluster = new GeneratorCluster();
            cluster.setGeneratorIndex(genId);
            cluster.setPrefix(baseKey.split("_")[0]);
            cluster.setNode(baseKey.split("_")[1]);

            // Trier les fichiers par séquence pour ce générateur
            genFiles.sort(Comparator.comparingInt(DateTimeSeqObj::getSequence));

            for (DateTimeSeqObj file : genFiles) {
                cluster.add(file);
            }

            // Calculer le score de continuité
            cluster.setContinuityScore(calculateContinuityScore(genFiles));

            clusters.add(cluster);
        }

        return clusters;
    }

    /**
     * Calcule le score de continuité pour un générateur.
     */
    private static double calculateContinuityScore(List<DateTimeSeqObj> files) {
        if (files.size() <= 1)
            return 1.0;

        int consecutiveCount = 0;
        for (int i = 1; i < files.size(); i++) {
            int gap = calculateSequenceGap(files.get(i - 1).getSequence(), files.get(i).getSequence());
            if (gap == 1) {
                consecutiveCount++;
            }
        }

        return (double) consecutiveCount / (files.size() - 1);
    }

    /**
     * Affiche un résumé de la détection.
     */
    private static void printDetectionSummary(List<GeneratorCluster> clusters) {
        System.out.println("\n--- Résumé de la détection ---");
        for (GeneratorCluster cluster : clusters) {
            if (!cluster.getFiles().isEmpty()) {
                int minSeq = cluster.getFiles().stream()
                        .mapToInt(DateTimeSeqObj::getSequence)
                        .min().orElse(0);
                int maxSeq = cluster.getFiles().stream()
                        .mapToInt(DateTimeSeqObj::getSequence)
                        .max().orElse(0);

                System.out.printf("  G%d: Seq[%d → %d] | Fichiers=%d | Score=%.2f%n",
                        cluster.getGeneratorIndex(),
                        minSeq, maxSeq,
                        cluster.getFiles().size(),
                        cluster.getContinuityScore());
            }
        }
        System.out.println("==============================\n");
    }
}
