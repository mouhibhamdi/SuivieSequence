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
@Table(name = "cdr_files_survey",
        uniqueConstraints = @UniqueConstraint(name = "u_cdr_files_survey", columnNames = {"date", "type"})
)
public class CdrFilesSurvey {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cdr_files_survey_seq")
    @SequenceGenerator(name = "cdr_files_survey_seq", sequenceName = "cdr_files_survey_seq", allocationSize = 1)
    private Long id;
    @Column(name = "date", columnDefinition = "DATE")
    private LocalDate date;
    private String type;
    private BigDecimal count;
    private BigDecimal missing;
//    @Column(name = "estimation_inf")
//    private BigDecimal estimationInf;
//    @Column(name = "estimation_sup")
//    private BigDecimal estimationSup;

    public CdrFilesSurvey(LocalDate date, String type) {
        this.date = date;
        this.type = type;
        count = BigDecimal.ZERO;
        missing = BigDecimal.ZERO;
//        estimationInf = BigDecimal.ZERO;
//        estimationSup = BigDecimal.ZERO;
    }

    public void addCount(int newSequenceSize) {
        this.count = this.count.add(BigDecimal.valueOf(newSequenceSize));
    }

    public void subtractMissing(int val){
        if (this.missing.compareTo(BigDecimal.ZERO) > 0) {
            this.missing = this.missing.subtract(BigDecimal.valueOf(val));
        }
    }

    public void addCountForMM(){
        this.count = this.count.add(BigDecimal.ONE);
        if(this.missing.compareTo(BigDecimal.ZERO) > 0){
            this.missing = this.missing.subtract(BigDecimal.ONE);
        }
    }

    public void addMissingForMM(){
        this.missing = this.missing.add(BigDecimal.ONE);
    }
}
