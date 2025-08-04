package com.advantek.suivieSequence.service.sequenceService;

import com.advantek.suivieSequence.entity.CdrFilesSurvey;
import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.repository.CdrFilesGroupIdRepo;
import com.advantek.suivieSequence.service.StatCdrFilesService;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class SuivieMobileMoney {
    private final CdrFilesGroupIdRepo cdrFilesGroupIdRepo;
    private final StatCdrFilesService statCdrFilesService;

    public SuivieMobileMoney(CdrFilesGroupIdRepo cdrFilesGroupIdRepo, StatCdrFilesService statCdrFilesService) {
        this.cdrFilesGroupIdRepo = cdrFilesGroupIdRepo;
        this.statCdrFilesService = statCdrFilesService;
    }

    public void suivieMM(CdrFilesTitleConfiguration cdrFileConfig, LocalDate yesterday){
        LocalDate lastValidSituationDate = this.statCdrFilesService.getLastExecutionDate(cdrFileConfig.getCdrName(), yesterday).plusDays(2);

        List<LocalDate> mmFiles = SequenceListUtils.getDateFiles(cdrFileConfig, lastValidSituationDate, yesterday);
        log.info("sequence list size {}", mmFiles.size());

        mmFiles.sort(LocalDate::compareTo);

        LocalDate itarationDate = lastValidSituationDate.plusDays(1);
        while (itarationDate.isBefore(yesterday.plusDays(1))){
        //for (LocalDate itarationDate=lastValidSituationDate.plusDays(1); itarationDate.isBefore(LocalDate.now()); itarationDate=itarationDate.plusDays(1)){
            CdrFilesSurvey cdrFilesSurvey = this.statCdrFilesService.getCdrFileSurveyByDateAndName(itarationDate, cdrFileConfig.getCdrName());
            if(mmFiles.contains(itarationDate)){
                log.info("File with date {} existe", itarationDate);
                cdrFilesSurvey.addCountForMM();
                log.info("cdrFilesSurvey ==> {}", cdrFilesSurvey);
                mmFiles.remove(itarationDate);
            }
            else {
                log.warn("File with date {} do not existe", itarationDate);
                cdrFilesSurvey.addMissingForMM();
                log.info("cdrFilesSurvey ==> {}", cdrFilesSurvey);
            }

            this.statCdrFilesService.saveCdrState(cdrFilesSurvey);
            itarationDate = itarationDate.plusDays(1);
        }

        log.info("{} date to be updated", mmFiles.size());
        if(mmFiles.isEmpty()){
            CdrFilesSurvey cdrFilesSurvey = new CdrFilesSurvey(yesterday.minusDays(1), cdrFileConfig.getCdrName());
            cdrFilesSurvey.addMissingForMM();
            this.statCdrFilesService.saveCdrState(cdrFilesSurvey);
        }
        for (LocalDate updateDate : mmFiles){
            CdrFilesSurvey cdrFilesSurvey = this.statCdrFilesService.getCdrFileSurveyByDateAndName(updateDate, cdrFileConfig.getCdrName());
            log.info("update missing date {}", updateDate);
            cdrFilesSurvey.addCountForMM();
            log.info("cdrFilesSurvey ==> {}", cdrFilesSurvey);
            this.statCdrFilesService.saveCdrState(cdrFilesSurvey);
        }
    }
}
