package com.advantek.suivieSequence.utils;

import com.advantek.suivieSequence.exception.RegularExpressionException;
import com.advantek.suivieSequence.model.DateTimeSeqObj;
import com.advantek.suivieSequence.model.SequenceObj;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                throw new RegularExpressionException("Regular expression can't find date and Sequence in the file name " + fileName + " \nREGULAR EXPRESSION ==> " + regularExpression);
            }

            sequenceObj.setDate(LocalDate.parse(matcher.group(1), DateTimeFormatter.ofPattern("yyyyMMdd")));
            sequenceObj.setSequence(Integer.parseInt(matcher.group(2)));

        } catch (Exception e) {
            log.error("Exception :", e);
            sequenceObj.setSequence(-1);
        }
        return sequenceObj;
    }
    public static DateTimeSeqObj extractDateTimeAndSequence(String fileName, String regularExpression){
        DateTimeSeqObj sequenceObj = new DateTimeSeqObj();
        try {
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);
            // throw except if not match
            if (!matcher.find()) {
                throw new RegularExpressionException("Regular expression can't find date and Sequence in the file name " + fileName + " \nREGULAR EXPRESSION ==> " + regularExpression);
            }

            String first = matcher.group(1).replaceAll("_", "").replaceAll("-", "");
            String second = matcher.group(2).replaceAll("_", "").replaceAll("-", "");
            if (first.length() >= 10) {
                sequenceObj.setDateTime(LocalDateTime.parse(getStrDateTime(first), DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
                sequenceObj.setSequence(Integer.parseInt(second));
            } else {
                sequenceObj.setDateTime(LocalDateTime.parse(getStrDateTime(second), DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
                sequenceObj.setSequence(Integer.parseInt(first));
            }
        } catch (Exception e) {
            log.error("Exception : {}", e.getMessage(), e);
            sequenceObj.setSequence(-1);
        }
        return sequenceObj;
    }

    public static int extractSequence(String fileName, String regularExpression){
        try{
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);
            if(!matcher.find()){
                throw new RegularExpressionException("Regular expression can't find Sequence in the file name " + fileName + " \nREGULAR EXPRESSION ==> " + regularExpression);
            }
            return Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            log.error("Exception : "+ e);
            return -1;
        }
    }

    public static LocalDate extractDate(String fileName, String regularExpression){
        try {
            Pattern pattern = Pattern.compile(regularExpression);
            Matcher matcher = pattern.matcher(fileName);
            // throw except if not match
            if (!matcher.find()) {
                throw new RegularExpressionException("Regular expression can't find date in the file name " + fileName + " \nREGULAR EXPRESSION ==> " + regularExpression);
            }
            String strDate = matcher.group(1);
            if (strDate.length() == 8){
                return LocalDate.parse(strDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            else {
                throw new RegularExpressionException("Error in date format " + strDate + "\nfile name : " + fileName + "\nregular expression: " + regularExpression);
            }
        } catch (Exception e) {
            log.error("Exception : {}", e.getMessage(), e);
            return null;
        }
    }

    private static String getStrDateTime(String date){
        if(date.length() == 10){
            return "20" + date + "00";
        } else if (date.length() == 12) {
            return date + "00";
        } else {
            return date;
        }
    }
}
