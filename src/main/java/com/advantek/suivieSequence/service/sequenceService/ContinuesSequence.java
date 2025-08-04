package com.advantek.suivieSequence.service.sequenceService;

import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.model.DateTimeSeqObj;
import com.advantek.suivieSequence.repository.CdrFilesGroupIdRepo;
import com.advantek.suivieSequence.service.StatCdrFilesService;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContinuesSequence {

    private final CdrFilesGroupIdRepo cdrFilesGroupIdRepo;
    private final StatCdrFilesService statService;

    private final int ecart = 1000;

    public ContinuesSequence(CdrFilesGroupIdRepo cdrFilesGroupIdRepo, StatCdrFilesService statService) {
        this.cdrFilesGroupIdRepo = cdrFilesGroupIdRepo;
        this.statService = statService;
    }

    public void suivieContinuesSeq(CdrFilesTitleConfiguration cdrFileConfig, LocalDate yesterday){
        LocalDate lastValidSituationDate = this.cdrFilesGroupIdRepo.readRecentDate(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud())
                .orElse(new CdrFilesGroupId(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), LocalDate.now().minusDays(2), 0)).getDate().plusDays(1);
        List<DateTimeSeqObj> sequenceObjList = SequenceListUtils.getContinuesSeqListForConfigurationBetween(cdrFileConfig, lastValidSituationDate, yesterday);
        log.info("sequence list size {}", sequenceObjList.size());

        if(!sequenceObjList.isEmpty()) {
            Map<LocalDate, List<DateTimeSeqObj>> map = new TreeMap<>(sequenceObjList.stream()
                    .collect(Collectors.groupingBy(item -> item.getDateTime().toLocalDate())));


            for (LocalDate date : map.keySet()) {
                log.info("Date {}", date);
                List<DateTimeSeqObj> dateTimeSeqObjs = map.get(date);

                Comparator<DateTimeSeqObj> comparator = Comparator
                        .comparing(DateTimeSeqObj::getDateTime)
                        .thenComparingInt(DateTimeSeqObj::getSequence);
                dateTimeSeqObjs.sort(comparator);
                CdrFilesGroupId last = this.cdrFilesGroupIdRepo.readRecentDate(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud())
                        .orElse(new CdrFilesGroupId(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), date.minusDays(1), 0));

                CdrFilesGroupId unreceivedSeqObj = this.cdrFilesGroupIdRepo
                        .findByCdrTypeAndCdrGroupAndCategoryAndNoeudAndDate(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), date)
                        .orElse(new CdrFilesGroupId(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), date, last.getMax() + 1));

                int previousUnreceived = unreceivedSeqObj.getUnreceivedSeqSet().size();

                if ((last.getMax() >= unreceivedSeqObj.getMin()) && (last.getDate().isBefore(unreceivedSeqObj.getDate()))) {
                    List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
                    for (int i = unreceivedSeqObj.getMin(); i < last.getMax() + 1; i++) {
                        unreceivedSeqSet.remove(Integer.valueOf(i));
                    }
                    unreceivedSeqObj.setMin(last.getMax() + 1);
                }
                if ((unreceivedSeqObj.getMax() == 1) && (unreceivedSeqObj.getMin() == 1)) {
                    try {
                        unreceivedSeqObj.setMin(dateTimeSeqObjs.get(0).getSequence());
                        unreceivedSeqObj.setMax(dateTimeSeqObjs.get(0).getSequence());
                        log.info("==> RESET MIN & MAX <==");
                    } catch (Exception e) {
                        log.error("", e);
                    }
                }
                log.info(
                        "\nCdrFilesGroupId ==> \nID: {}; \nDate: {}; \nCDR Type: {}; \nCDR Group: {}; \nCategory: {}; \nMAX: {}; \nMin: {}; \nUnreceived size: {}",
                        unreceivedSeqObj.getId(), unreceivedSeqObj.getDate(), unreceivedSeqObj.getCdrType(), unreceivedSeqObj.getCdrGroup(), unreceivedSeqObj.getCategory(), unreceivedSeqObj.getMax(), unreceivedSeqObj.getMin(), unreceivedSeqObj.getUnreceivedSeqSet().size()
                );

                for (DateTimeSeqObj seq : dateTimeSeqObjs) {
                    /* seq continue */
                    if (seq.getSequence() > unreceivedSeqObj.getMax()) {
                        if (seq.getDateTime().equals(unreceivedSeqObj.getMaxDateTime()) && isRestCounter(unreceivedSeqObj, seq.getSequence(), cdrFileConfig.getMaxFilesNumber())) {
                            seqContinuesRemoveReceived(unreceivedSeqObj, seq);
                        } else if (seq.getDateTime().isBefore(unreceivedSeqObj.getMaxDateTime())) {
                            seqContinuesRemoveReceived(unreceivedSeqObj, seq);
                        } else {
                            seqContinuesAddListUnreceived(unreceivedSeqObj, seq);
                        }
                    }
                    /* seq m<n en retard */
                    else if ((seq.getSequence() < unreceivedSeqObj.getMax()) && (!seq.getDateTime().isAfter(unreceivedSeqObj.getMaxDateTime()))) {
                        seqContinuesRemoveReceived(unreceivedSeqObj, seq);
                    }
                    /* new seq start  */
                    else if ((seq.getSequence() < unreceivedSeqObj.getMax()) && (seq.getDateTime().isAfter(unreceivedSeqObj.getMaxDateTime()))) {
                        newSeqStart(unreceivedSeqObj, seq, cdrFileConfig.getMaxFilesNumber());
                    }
                }
                log.info(
                        "\nEND ==> CdrFilesGroupId ==> \nID: {}; \nDate: {}; \nCDR Type: {}; \nCDR Group: {}; \nCategory: {}; \nMAX: {}; \nMin: {}; \nUnreceived size: {}",
                        unreceivedSeqObj.getId(), unreceivedSeqObj.getDate(), unreceivedSeqObj.getCdrType(), unreceivedSeqObj.getCdrGroup(), unreceivedSeqObj.getCategory(), unreceivedSeqObj.getMax(), unreceivedSeqObj.getMin(), unreceivedSeqObj.getUnreceivedSeqSet().size()
                );
                this.cdrFilesGroupIdRepo.save(unreceivedSeqObj);
                this.statService.updateCdrStat(unreceivedSeqObj, cdrFileConfig.getCdrName(), previousUnreceived, dateTimeSeqObjs.size());
            }
        }
        else {
            this.statService.calculEstimateNotReceived(cdrFileConfig.getCdrName());
        }
    }

    private boolean isRestCounter(CdrFilesGroupId unreceivedSeqObj,  int sequence, int maxNbrFile){
        boolean endOfTheSequence = (maxNbrFile - sequence) < ecart;
        boolean startOfNewSequence = unreceivedSeqObj.getMax() < ecart;
        return endOfTheSequence && startOfNewSequence;
    }

    private void seqContinuesAddListUnreceived(CdrFilesGroupId unreceivedSeqObj, DateTimeSeqObj seqObj){
        List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
        for(int i=unreceivedSeqObj.getMax()+1; i< seqObj.getSequence(); i++){ //si seq.getSequence == (newMax+1) alors on vas pas entrer dans la boucle for
            unreceivedSeqSet.add(i);
        }
        unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
        unreceivedSeqObj.setMax(seqObj.getSequence());
        unreceivedSeqObj.setMaxDateTime(seqObj.getDateTime());
    }

    private void seqContinuesRemoveReceived(CdrFilesGroupId unreceivedSeqObj, DateTimeSeqObj seqObj){
        List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
        unreceivedSeqSet.remove(Integer.valueOf(seqObj.getSequence()));
        unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
    }
    private void newSeqStart(CdrFilesGroupId unreceivedSeqObj, DateTimeSeqObj seqObj, int maxNbrFile){
        List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
        for(int i= unreceivedSeqObj.getMax()+1; i<maxNbrFile; i++){
            unreceivedSeqSet.add(i);
        }
        for (int i=1; i< seqObj.getSequence(); i++){
            unreceivedSeqSet.add(i);
        }
        unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
        unreceivedSeqObj.setMax(seqObj.getSequence());
        unreceivedSeqObj.setMaxDateTime(seqObj.getDateTime());
    }
}
