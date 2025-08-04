package com.advantek.suivieSequence.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@Table(name = "cdrfiles_group_id")
public class CdrFilesGroupId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "date")
    private LocalDate date;
    @Column(nullable = false, name = "cdr_type")
    private String cdrType;
    @Column(nullable = false, name = "cdr_group")
    private String cdrGroup;
    @Column(name = "category")
    private String category;
    @Column(name = "noeud")
    private String noeud;
    @Column(nullable = false, name = "unreceived_seq_set")
    @ElementCollection(fetch = FetchType.EAGER)
    private List<Integer> unreceivedSeqSet;
    @Column(nullable = false)
    private int max;
    @Column
    private LocalDateTime maxDateTime;
    @Column
    private int min;

    public CdrFilesGroupId(String cdrType, String cdrGroup, String category, String noeud, LocalDate date) {
        this.cdrType = cdrType;
        this.cdrGroup = cdrGroup;
        this.category = category;
        this.noeud = noeud;
        this.date = date;
        this.max = 1;
        this.maxDateTime = LocalDate.now().atStartOfDay();
        this.min = 0;
        this.unreceivedSeqSet = new ArrayList<>();
    }

    public CdrFilesGroupId(String cdrType, String cdrGroup, String category, String noeud, LocalDate date,  int min) {
        this.date = date;
        this.cdrType = cdrType;
        this.cdrGroup = cdrGroup;
        this.category = category;
        this.noeud = noeud;
        this.max = min;
        this.maxDateTime = date.atStartOfDay();
        this.min = min;
        this.unreceivedSeqSet = new ArrayList<>();
    }

    public boolean isResetCounterCase(){
        return this.min > this.max;
    }
}
