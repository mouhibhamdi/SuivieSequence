package com.advantek.suivieSequence.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class SequenceObj {
    private LocalDate date;
    private int sequence;
}
