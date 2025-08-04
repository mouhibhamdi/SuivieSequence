package com.advantek.suivieSequence.utils;

import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.exception.NoFileFoundException;
import com.advantek.suivieSequence.model.DateTimeSeqObj;
import com.advantek.suivieSequence.model.SequenceObj;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class SequenceListUtils {
    public static List<DateTimeSeqObj> getContinuesSeqListForConfigurationBetween(CdrFilesTitleConfiguration cdrFileConfig, LocalDate lastValidSituationDate, LocalDate yesterday){
        log.info("Start reading files");
        List<DateTimeSeqObj> sequenceObjList = new ArrayList<>();

        while (!yesterday.isBefore(lastValidSituationDate)) {
            String fileName = null;
            try {
                fileName = cdrFileName(cdrFileConfig, lastValidSituationDate);
                log.info("file name {}", fileName);

                Stream<String> cdrFileNameStream = Files.lines(Paths.get(fileName), StandardCharsets.UTF_8);

                cdrFileNameStream.filter(item -> (item.contains(cdrFileConfig.getCdrGroup())) && (item.contains(cdrFileConfig.getCategory())))
                        .toList()
                        .forEach(item -> {
                            DateTimeSeqObj dateTimeSeqObj = RegularExpressionUtils.extractDateTimeAndSequence(item, cdrFileConfig.getRegularExpression());
                            if (dateTimeSeqObj.getSequence() != -1) {
                                sequenceObjList.add(dateTimeSeqObj);
                            }
                        });
            } catch (IOException | NoFileFoundException e) {
                log.error("Can't open file {}", fileName, e);
            }
            lastValidSituationDate = lastValidSituationDate.plusDays(1);
        }
        return sequenceObjList;
    }

    public static List<Integer> getNoDateContinuesSeqList(CdrFilesTitleConfiguration cdrFileConfig, LocalDate date){
        log.info("Start reading files");
        List<Integer> integerList = new ArrayList<>();
        String fileName = null;
        try {
        fileName = cdrFileName(cdrFileConfig, date);
        log.info("file name {}", fileName);
            Stream<String> cdrFileNameStream = Files.lines(Paths.get(fileName), StandardCharsets.UTF_8);
            cdrFileNameStream
                    .filter(item -> (item.startsWith(cdrFileConfig.getCategory())) && (item.contains(cdrFileConfig.getCategory())))
                    .toList().forEach(item -> {
                        int integer = RegularExpressionUtils.extractSequence(item, cdrFileConfig.getRegularExpression());
                        if (integer != -1) {
                            integerList.add(integer);
                        }
                    });
        } catch (IOException | NoFileFoundException e) {
            log.error("Can't open file {}", fileName, e);
        }
        return integerList;
    }

    public static List<SequenceObj> getLimitedSeqList(CdrFilesTitleConfiguration cdrFileConfig, LocalDate date, LocalDate startDate){
        log.info("Start reading files");
        String fileName = null;
        List<SequenceObj> sequenceObjList = new ArrayList<>();
        while (!date.isBefore(startDate)) {
            try {
                fileName = cdrFileName(cdrFileConfig, startDate);
                log.info("file name {}", fileName);
                Stream<String> cdrFileNameStream = Files.lines(Paths.get(fileName));
                List<String> list = cdrFileNameStream.toList();
                list.stream()
                        .filter(item -> item.contains(cdrFileConfig.getCdrGroup()))
                        .toList()
                        .forEach(item -> {
                            SequenceObj sequenceObj;
                            sequenceObj = RegularExpressionUtils.extractDateAndSequence(item, cdrFileConfig.getRegularExpression());
                            if (sequenceObj.getSequence() != -1) {
                                sequenceObjList.add(sequenceObj);
                            }
                        });
            } catch (IOException | NoFileFoundException e) {
                log.error("Can't open file {}", fileName, e);
            }
            startDate = startDate.plusDays(1);
        }
        return sequenceObjList;
    }

    public static List<LocalDate> getDateFiles(CdrFilesTitleConfiguration cdrFileConfig, LocalDate lastValidSituationDate, LocalDate yesterday){
        log.info("Start reading files");
        List<LocalDate> sequenceObjList = new ArrayList<>();

        while (!yesterday.isBefore(lastValidSituationDate)) {
            String fileName = null;
            try {
                fileName = cdrFileName(cdrFileConfig, lastValidSituationDate);
                log.info("file name {}", fileName);

                Stream<String> cdrFileNameStream = Files.lines(Paths.get(fileName), StandardCharsets.UTF_8);

                cdrFileNameStream.filter(item -> (item.contains(cdrFileConfig.getCdrGroup())) && (item.contains(cdrFileConfig.getCategory())))
                        .toList()
                        .forEach(item -> {
                            LocalDate mmFileDate = RegularExpressionUtils.extractDate(item, cdrFileConfig.getRegularExpression());
                            if (mmFileDate != null) {
                                sequenceObjList.add(mmFileDate);
                            }
                        });
            } catch (IOException | NoFileFoundException e) {
                log.error("Can't open file {}", fileName, e);
            }
            lastValidSituationDate = lastValidSituationDate.plusDays(1);
        }
        return sequenceObjList;
    }

    private static String cdrFileName(CdrFilesTitleConfiguration cdrFileConfig, LocalDate date){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        File[] files = Objects.requireNonNull(
                new File(cdrFileConfig.getDirectory()).listFiles(item -> item.getName().contains(formatter.format(date)))
        );
        if(files.length == 0){
            throw new NoFileFoundException("No file found for date " + formatter.format(date) + "\n Directory " + cdrFileConfig.getDirectory());
        }
        return files[0].getAbsolutePath();
    }
}
