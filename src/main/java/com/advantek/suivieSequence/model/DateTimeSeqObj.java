package com.advantek.suivieSequence.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DateTimeSeqObj {

    private LocalDateTime dateTime;
    private int sequence;
}
