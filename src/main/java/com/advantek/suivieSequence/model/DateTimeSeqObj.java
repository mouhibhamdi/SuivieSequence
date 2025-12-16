package com.advantek.suivieSequence.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DateTimeSeqObj {
    private String prefix;
    private String node;
    private int sequence;
    private LocalDateTime dateTime;
    private int generatorId;
}
