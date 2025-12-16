package com.advantek.suivieSequence.utils;

import com.advantek.suivieSequence.exception.RegularExpressionException;
import com.advantek.suivieSequence.model.DateTimeSeqObj;
import com.advantek.suivieSequence.model.SequenceObj;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RegularExpressionUtils {

    public static SequenceObj extractDateAndSequence(String fileName, String regularExpression){
        SequenceObj sequenceObj = new SequenceObj();
        try{
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);
            if(!matcher.find()){
                throw new RegularExpressionException(
                        "Regular expression can't find date and Sequence in the file name " + fileName +
                                " \nREGULAR EXPRESSION ==> " + regularExpression
                );
            }

            sequenceObj.setDate(LocalDate.parse(matcher.group(1), DateTimeFormatter.ofPattern("yyyyMMdd")));
            sequenceObj.setSequence(Integer.parseInt(matcher.group(2)));

        } catch (Exception e) {
            log.error("Exception :", e);
            sequenceObj.setSequence(-1);
        }
        return sequenceObj;
    }

    public static DateTimeSeqObj extractDateTimeAndSequence(String fileName, String regularExpression) {
        DateTimeSeqObj sequenceObj = new DateTimeSeqObj();
        try {
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);

            if (!matcher.find()) {
                throw new RegularExpressionException(
                        "Regular expression can't find date, node and Sequence in the file name " + fileName +
                                " \nREGULAR EXPRESSION ==> " + regularExpression
                );
            }

            if (matcher.groupCount() < 4) {
                throw new RegularExpressionException(
                        "Regex must have at least 4 groups (prefix, node, sequence, datetime). Found: " + matcher.groupCount()
                );
            }

            sequenceObj.setPrefix(matcher.group(1));
            sequenceObj.setNode(matcher.group(2));
            sequenceObj.setSequence(Integer.parseInt(matcher.group(3)));

            String datetimeStr = matcher.group(4);
            if (datetimeStr.length() < 14) {
                throw new DateTimeParseException("Datetime part too short", datetimeStr, 0);
            } else if (datetimeStr.length() > 14) {
                datetimeStr = datetimeStr.substring(0, 14);
            }

            sequenceObj.setDateTime(LocalDateTime.parse(
                    datetimeStr,
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            ));

        } catch (Exception e) {
            log.error("Exception parsing file {}: {}", fileName, e.getMessage(), e);
            sequenceObj.setSequence(-1); // valeur par défaut en cas d'erreur
        }
        return sequenceObj;
    }

    public static int extractSequence(String fileName, String regularExpression){
        try{
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);
            if(!matcher.find()){
                throw new RegularExpressionException(
                        "Regular expression can't find Sequence in the file name " + fileName +
                                " \nREGULAR EXPRESSION ==> " + regularExpression
                );
            }
            return Integer.parseInt(matcher.group(3)); // Séquence sur 5 digits
        } catch (Exception e) {
            log.error("Exception : "+ e);
            return -1;
        }
    }

    public static LocalDate extractDate(String fileName, String regularExpression){
        try {
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);

            if (!matcher.find()) {
                throw new RegularExpressionException(
                        "Regular expression can't find date in the file name " + fileName +
                                " \nREGULAR EXPRESSION ==> " + regularExpression
                );
            }

            String strDate = matcher.group(4).substring(0, 8);
            return LocalDate.parse(strDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

        } catch (Exception e) {
            log.error("Exception : {}", e.getMessage(), e);
            return null;
        }
    }
}
