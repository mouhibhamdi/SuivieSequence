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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class SequenceListUtils {

    // ----------------------------------------------------------------------
    // LOADER GLOBAL
    // ----------------------------------------------------------------------

    public static List<DateTimeSeqObj> loadAllFiles(
            List<CdrFilesTitleConfiguration> configs,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<DateTimeSeqObj> result = new ArrayList<>();
        Set<Path> allTxtFiles = new HashSet<>();

        // Récupérer tous les fichiers .txt des dossiers de toutes les configs
        for (CdrFilesTitleConfiguration cfg : configs) {
            Path folder = Paths.get(cfg.getDirectory());
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".txt"))
                        .forEach(allTxtFiles::add);
            } catch (IOException e) {
                log.error("Directory scan error for {}", cfg.getDirectory(), e);
            }
        }

        // Parcourir chaque fichier .txt
        for (Path file : allTxtFiles) {
            LocalDate fileDate = extractDateFromFilename(file); // yyyy-MM-dd depuis le nom
            if (fileDate == null || fileDate.isBefore(startDate) || fileDate.isAfter(endDate)) {
                continue;
            }

            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                lines.forEach(line -> {
                    // Identifier la config correspondant au prefix du fichier
                    Optional<CdrFilesTitleConfiguration> matchedConfig = configs.stream()
                            .filter(cfg -> line.startsWith(cfg.getCdrName()))
                            .findFirst();

                    if (matchedConfig.isPresent()) {
                        try {
                            DateTimeSeqObj obj = RegularExpressionUtils
                                    .extractDateTimeAndSequence(line, matchedConfig.get().getRegularExpression());
                            if (obj != null && obj.getSequence() != -1) {
                                result.add(obj);
                            } else {
                                log.warn("Failed to parse line with regex: {}", line);
                            }
                        } catch (Exception e) {
                            log.error("Error parsing line {}: {}", line, e.getMessage());
                        }
                    } else {
                        log.warn("No config matched for line: {}", line);
                    }
                });
            } catch (IOException e) {
                log.error("Error reading file {}", file, e);
            }
        }

        return result;
    }

    // ----------------------------------------------------------------------
    // Extraire yyyy-MM-dd depuis filename (si possible)
    // ----------------------------------------------------------------------

    private static LocalDate extractDateFromFilename(Path file) {
        String name = file.getFileName().toString();

        if (name.matches("\\d{4}-\\d{2}-\\d{2}\\.txt")) {
            return LocalDate.parse(name.substring(0, 10));
        }
        return null;
    }

    public static Optional<LocalDate> getFirstExistingDateForConfiguration(CdrFilesTitleConfiguration cdrFileConfig) {
        Path folder = Paths.get(cdrFileConfig.getDirectory()); // le path dynamique
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.matches("\\d{4}-\\d{2}-\\d{2}\\.txt")) // fichiers CDR uniquement
                    .map(name -> LocalDate.parse(name.substring(0, 10))) // yyyy-MM-dd
                    .min(LocalDate::compareTo);
        } catch (IOException e) {
            log.error("Error scanning folder for first existing date", e);
            return Optional.empty();
        }
    }

    public static Optional<LocalDate> getLastExistingDateForConfiguration(CdrFilesTitleConfiguration cdrFileConfig) {
        Path folder = Paths.get(cdrFileConfig.getDirectory());
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.matches("\\d{4}-\\d{2}-\\d{2}\\.txt"))
                    .map(name -> LocalDate.parse(name.substring(0, 10)))
                    .max(LocalDate::compareTo);
        } catch (IOException e) {
            log.error("Error scanning folder for last existing date", e);
            return Optional.empty();
        }
    }


    // ----------------------------------------------------------------------
    // NO DATE CONTINUES SEQ
    // ----------------------------------------------------------------------

    public static List<Integer> getNoDateContinuesSeqList(
            CdrFilesTitleConfiguration cdrFileConfig, LocalDate date) {

        log.info("Start reading files");
        List<Integer> integerList = new ArrayList<>();
        String fileName = null;

        try {
            fileName = cdrFileName(cdrFileConfig, date);
            log.info("file name {}", fileName);

            Stream<String> cdrFileNameStream =
                    Files.lines(Paths.get(fileName), StandardCharsets.UTF_8);

            cdrFileNameStream
                    .filter(item -> item.contains(cdrFileConfig.getCdrGroup()))
                    .forEach(item -> {
                        int integer =
                                RegularExpressionUtils.extractSequence(
                                        item,
                                        cdrFileConfig.getRegularExpression()
                                );
                        if (integer != -1) {
                            integerList.add(integer);
                        }
                    });

        } catch (IOException | NoFileFoundException e) {
            log.error("Can't open file {}", fileName, e);
        }

        return integerList;
    }

    // ----------------------------------------------------------------------
    // LIMITED SEQ
    // ----------------------------------------------------------------------

    public static List<SequenceObj> getLimitedSeqList(
            CdrFilesTitleConfiguration cdrFileConfig,
            LocalDate date,
            LocalDate startDate) {

        log.info("Start reading files");
        List<SequenceObj> sequenceObjList = new ArrayList<>();
        String fileName = null;

        while (!date.isBefore(startDate)) {
            try {
                fileName = cdrFileName(cdrFileConfig, startDate);
                log.info("file name {}", fileName);

                Stream<String> stream = Files.lines(Paths.get(fileName));

                stream
                        .filter(item -> item.contains(cdrFileConfig.getCdrGroup()))
                        .forEach(item -> {
                            SequenceObj sequenceObj =
                                    RegularExpressionUtils.extractDateAndSequence(
                                            item,
                                            cdrFileConfig.getRegularExpression()
                                    );

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

    // ----------------------------------------------------------------------
    // DATE FILES FOR MOBILE MONEY
    // ----------------------------------------------------------------------

    public static List<LocalDate> getDateFiles(
            CdrFilesTitleConfiguration cdrFileConfig,
            LocalDate lastValidSituationDate,
            LocalDate yesterday) {

        log.info("Start reading files");
        List<LocalDate> sequenceObjList = new ArrayList<>();

        while (!yesterday.isBefore(lastValidSituationDate)) {

            String fileName = null;

            try {
                fileName = cdrFileName(cdrFileConfig, lastValidSituationDate);
                log.info("file name {}", fileName);

                Stream<String> stream =
                        Files.lines(Paths.get(fileName), StandardCharsets.UTF_8);

                stream
                        .filter(item -> item.contains(cdrFileConfig.getCdrGroup()))
                        .forEach(item -> {
                            LocalDate fileDate =
                                    RegularExpressionUtils.extractDate(
                                            item,
                                            cdrFileConfig.getRegularExpression()
                                    );

                            if (fileDate != null) {
                                sequenceObjList.add(fileDate);
                            }
                        });

            } catch (IOException | NoFileFoundException e) {
                log.error("Can't open file {}", fileName, e);
            }

            lastValidSituationDate = lastValidSituationDate.plusDays(1);
        }

        return sequenceObjList;
    }

    private static String cdrFileName(CdrFilesTitleConfiguration cdrFileConfig, LocalDate date) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        File directory = new File(cdrFileConfig.getDirectory());

        if (!directory.exists()) {
            throw new NoFileFoundException(
                    "Directory NOT FOUND: " + cdrFileConfig.getDirectory()
            );
        }

        if (!directory.isDirectory()) {
            throw new NoFileFoundException(
                    "Path is NOT a directory: " + cdrFileConfig.getDirectory()
            );
        }

        File[] files = directory.listFiles(
                f -> f.getName().contains(formatter.format(date))
        );

        if (files == null || files.length == 0) {
            throw new NoFileFoundException(
                    "No file found for date " + formatter.format(date)
                            + " in directory " + cdrFileConfig.getDirectory()
            );
        }

        return files[0].getAbsolutePath();
    }
}
