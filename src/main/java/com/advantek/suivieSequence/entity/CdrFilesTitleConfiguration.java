package com.advantek.suivieSequence.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Data
@NoArgsConstructor
@Table(name = "cdrfiles_title_configuration")
public class CdrFilesTitleConfiguration implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;
    @Column(nullable = false, name = "cdr_name")
    private String cdrName;
    @Column(nullable = false, name = "cdr_type")
    private String cdrType;
    @Column(nullable = false, name = "cdr_group")
    private String cdrGroup;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String noeud;
    @Column()
    private String directory;
    @Column(nullable = false, name ="regular_expression" )
    private String regularExpression;
    @Column(name = "continues_sequence")
    private boolean continuesSequence;
    @Column(name = "no_date_continues_sequence")
    private boolean noDateContinuesSequence;
    @Column(name = "max_file_number")
    private int maxFilesNumber;
}
