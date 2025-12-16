package com.advantek.suivieSequence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(
        name = "cdr_files_survey",
        uniqueConstraints = @UniqueConstraint(
                name = "u_cdr_files_survey",
                columnNames = {"date", "type", "node"} // ajout du noeud dans l'unicité
        )
)
public class CdrFilesSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cdr_files_survey_seq")
    @SequenceGenerator(name = "cdr_files_survey_seq", sequenceName = "cdr_files_survey_seq", allocationSize = 1)
    private Long id;

    @Column(name = "date", columnDefinition = "DATE")
    private LocalDate date;

    private String type;

    private String node; // nouveau champ pour identifier le noeud

    @Column(nullable = false)
    private BigDecimal count = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal missing = BigDecimal.ZERO;

    public CdrFilesSurvey(LocalDate date, String type) {
        this.date = date;
        this.type = type;
        this.count = BigDecimal.ZERO;
        this.missing = BigDecimal.ZERO;
    }

    /** Ajouter des fichiers reçus */
    public void addCount(int newSequenceSize) {
        if (newSequenceSize > 0) {
            this.count = this.count.add(BigDecimal.valueOf(newSequenceSize));
        }
    }

    /** Réduire le nombre de missing */
    public void subtractMissing(int val){
        if (val <= 0) return;
        this.missing = this.missing.subtract(BigDecimal.valueOf(val));
        if (this.missing.compareTo(BigDecimal.ZERO) < 0) {
            this.missing = BigDecimal.ZERO; // sécurité
        }
    }

    /** Pour MM : 1 seul fichier reçu */
    public void addCountForMM(){
        this.count = this.count.add(BigDecimal.ONE);
        if (this.missing.compareTo(BigDecimal.ZERO) > 0) {
            this.missing = this.missing.subtract(BigDecimal.ONE);
        }
    }

    /** Pour MM : un fichier manquant */
    public void addMissingForMM(){
        this.missing = this.missing.add(BigDecimal.ONE);
    }
}
