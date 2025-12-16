package com.advantek.suivieSequence.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GeneratorCluster {

    private int generatorIndex; // G1, G2, G3...
    private String prefix;
    private String node;

    private List<DateTimeSeqObj> files = new ArrayList<>();
    private int lastSequence = -1;
    private int minSequence = Integer.MAX_VALUE;
    private int maxSequence = Integer.MIN_VALUE;
    private double continuityScore = 0.0; // Score de continuité (0.0 - 1.0)

    public boolean canAccept(DateTimeSeqObj f) {
        // Un cluster accepte un fichier seulement si la suite reste croissante
        return lastSequence == -1 || f.getSequence() >= lastSequence;
    }

    public void add(DateTimeSeqObj f) {
        files.add(f);
        lastSequence = f.getSequence();

        // Mettre à jour les bornes min/max
        if (f.getSequence() < minSequence) {
            minSequence = f.getSequence();
        }
        if (f.getSequence() > maxSequence) {
            maxSequence = f.getSequence();
        }
    }
}
