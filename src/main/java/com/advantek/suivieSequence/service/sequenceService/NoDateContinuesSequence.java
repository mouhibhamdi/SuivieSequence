package com.advantek.suivieSequence.service.sequenceService;

import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.exception.NoDateContinuesSequenceException;
import com.advantek.suivieSequence.repository.CdrFilesGroupIdRepo;
import com.advantek.suivieSequence.service.StatCdrFilesService;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NoDateContinuesSequence {

    private final CdrFilesGroupIdRepo cdrFilesGroupIdRepo;
    private final StatCdrFilesService statService;

    private int ecart = 1000; // x-y < ecart ==> x est proche de y

    public NoDateContinuesSequence(CdrFilesGroupIdRepo cdrFilesGroupIdRepo, StatCdrFilesService statService) {
        this.cdrFilesGroupIdRepo = cdrFilesGroupIdRepo;
        this.statService = statService;
    }

    public void suivieNoDateContinuesSeq(CdrFilesTitleConfiguration cdrFileConfig, LocalDate localDate){
        List<Integer> sequenceList = new ArrayList<>(SequenceListUtils.getNoDateContinuesSeqList(cdrFileConfig, localDate));
        if(cdrFileConfig.getCdrType().equals("b")){
            if (cdrFileConfig.getNoeud().equals("noeud_1")) {
                sequenceList = sequenceList.stream()
                        .filter(item -> item <= 25000000).collect(Collectors.toList());
            } else {
                sequenceList = sequenceList.stream()
                        .filter(item -> item > 25000000).collect(Collectors.toList());
            }
        } else if (cdrFileConfig.getCdrType().equals("SITE1_MSC2")) {
            sequenceList.remove(Integer.valueOf(20698185));
            sequenceList.remove(Integer.valueOf(20700145));
            sequenceList.remove(Integer.valueOf(20702615));
        }
        log.info("sequenceList size {}", sequenceList.size());

        if (!sequenceList.isEmpty()) {
            Collections.sort(sequenceList);
//            LocalDate date = LocalDate.now().minusDays(1);

            CdrFilesGroupId last = this.cdrFilesGroupIdRepo.readRecentDate(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud())
                    .orElse(new CdrFilesGroupId(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), localDate, 0));

            CdrFilesGroupId unreceivedSeqObj = this.cdrFilesGroupIdRepo
                    .findByCdrTypeAndCdrGroupAndCategoryAndNoeudAndDate(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), localDate)
                    .orElse(new CdrFilesGroupId(cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud(), localDate, last.getMax() + 1));

            int previousUnreceived = unreceivedSeqObj.getUnreceivedSeqSet().size();
            if ((unreceivedSeqObj.getMax() == 1) && (unreceivedSeqObj.getMin() == 1)) {
                try {
                    unreceivedSeqObj.setMin(Collections.min(sequenceList));
                    unreceivedSeqObj.setMax(Collections.min(sequenceList));
                } catch (Exception e) {
                    log.error("", e);
                }
            }
            log.info(
                    "\nCdrFilesGroupId ==> \nID: {}; \nDate: {}; \nCDR Type: {}; \nCDR Group: {}; \nCategory: {}; \nNoeud: {}; \nMAX: {}; \nMin: {}; \nUnreceived size: {}",
                    unreceivedSeqObj.getId(), unreceivedSeqObj.getDate(), unreceivedSeqObj.getCdrType(), unreceivedSeqObj.getCdrGroup(), unreceivedSeqObj.getNoeud(), unreceivedSeqObj.getCategory(), unreceivedSeqObj.getMax(), unreceivedSeqObj.getMin(), unreceivedSeqObj.getUnreceivedSeqSet().size()
            );
            List<Integer> oldSeqList = sequenceList.stream().filter(item -> item < unreceivedSeqObj.getMin()).collect(Collectors.toList());
            log.info("Old sequence size {}", oldSeqList.size());
            sequenceList.removeAll(oldSeqList);
            log.info("New sequence size {}", sequenceList.size());
            for (int sequence : sequenceList) {
                if (unreceivedSeqObj.isResetCounterCase()) {
                    if ((sequence >= unreceivedSeqObj.getMin()) || (sequence <= unreceivedSeqObj.getMax())) {
                        seqContinuesRemoveReceived(unreceivedSeqObj, sequence);
                    } else {
                        updatePreviousDate(unreceivedSeqObj, sequence, cdrFileConfig);
                    }
                } else {
                    if ((sequence >= unreceivedSeqObj.getMin()) && (sequence <= unreceivedSeqObj.getMax())) {
                        seqContinuesRemoveReceived(unreceivedSeqObj, sequence);
                    } else if (sequence > unreceivedSeqObj.getMax()) {
                        seqContinuesAddListUnreceived(unreceivedSeqObj, sequence);
                    } else if ((sequence < unreceivedSeqObj.getMin()) && (!isStartingNewSeq(unreceivedSeqObj, sequence, cdrFileConfig.getMaxFilesNumber()))) {
                        updatePreviousDate(unreceivedSeqObj, sequence, cdrFileConfig);
                    } else if ((sequence < unreceivedSeqObj.getMin()) && (isStartingNewSeq(unreceivedSeqObj, sequence, cdrFileConfig.getMaxFilesNumber()))) {
                        newSeqStart(unreceivedSeqObj, sequence, cdrFileConfig.getMaxFilesNumber());
                    }
                }
            }
            log.info(
                    "\nEND ==> CdrFilesGroupId ==> \nID: {}; \nDate: {}; \nCDR Type: {}; \nCDR Group: {}; \nCategory: {}; \nNoeud: {}; \nMAX: {}; \nMin: {}; \nUnreceived size: {}",
                    unreceivedSeqObj.getId(), unreceivedSeqObj.getDate(), unreceivedSeqObj.getCdrType(), unreceivedSeqObj.getCdrGroup(), unreceivedSeqObj.getCategory(), unreceivedSeqObj.getNoeud(), unreceivedSeqObj.getMax(), unreceivedSeqObj.getMin(), unreceivedSeqObj.getUnreceivedSeqSet().size()
            );
            while (!oldSeqList.isEmpty()){
                try {
                    CdrFilesGroupId cdrFilesGroupId = this.cdrFilesGroupIdRepo.findBySeqBetweenMaxAndMin(oldSeqList.get(0), cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup(), cdrFileConfig.getCategory(), cdrFileConfig.getNoeud())
                            .orElse(new CdrFilesGroupId());
                    if(cdrFilesGroupId.getId() != null){
                        List<Integer> list = oldSeqList.stream().filter(item -> item < cdrFilesGroupId.getMax()).collect(Collectors.toList());
                        oldSeqList.removeAll(list);
                        List<Integer> unreceviedSeq = cdrFilesGroupId.getUnreceivedSeqSet();
                        unreceviedSeq.removeAll(list);
                        cdrFilesGroupId.setUnreceivedSeqSet(unreceviedSeq);
                        this.statService.updateNoDateRecivedLate(cdrFilesGroupId, cdrFileConfig.getCdrName(), list.size());
                    }
                    else {
                        oldSeqList.remove(0);
                    }
                } catch (NoDateContinuesSequenceException e) {
                    oldSeqList.remove(0);
                    log.error("No date continues sequence\n => Can't find date of sequence {}", oldSeqList.get(0));
                }
                finally {
                    log.info("old sequence liste size {}", oldSeqList.size());
                }
            }

            this.cdrFilesGroupIdRepo.save(unreceivedSeqObj);

//            if (unreceivedSeqObj.getUnreceivedSeqSet().size() > 200000) {
//                log.warn("unreserved sequence length is to longe");
//                unreceivedSeqObj.setUnreceivedSeqSet(new ArrayList<>());
//            }

            this.statService.updateCdrStat(unreceivedSeqObj, cdrFileConfig.getCdrName(), previousUnreceived, sequenceList.size());
        }
//        else {
//            this.statService.calculEstimateNotReceived(cdrFileConfig.getCdrName());
//        }
    }

    private boolean isStartingNewSeq(CdrFilesGroupId unreceivedSeqObj,  int sequence, int maxNbrFile){
        boolean endOfTheSequence = (maxNbrFile - unreceivedSeqObj.getMax()) < ecart;
        boolean startOfNewSequence = sequence < ecart;
        return endOfTheSequence && startOfNewSequence;
    }

    private void seqContinuesAddListUnreceived(CdrFilesGroupId unreceivedSeqObj, int sequence){
        List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
        for(int i=unreceivedSeqObj.getMax()+1; i< sequence; i++){ //si seq.getSequence == (newMax+1) alors on vas pas entrer dans la boucle for
            unreceivedSeqSet.add(i);
        }
        unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
        unreceivedSeqObj.setMax(sequence);
    }

    private void seqContinuesRemoveReceived(CdrFilesGroupId unreceivedSeqObj, int sequence){
        List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
        unreceivedSeqSet.remove(Integer.valueOf(sequence));
        unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
    }

    private void newSeqStart(CdrFilesGroupId unreceivedSeqObj, Integer sequence, int maxNbrFile){
        List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
        for(int i= unreceivedSeqObj.getMax()+1; i<maxNbrFile; i++){
            unreceivedSeqSet.add(i);
        }
        for (int i=1; i< sequence; i++){
            unreceivedSeqSet.add(i);
        }
        unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
        unreceivedSeqObj.setMax(sequence);
    }

    private void updatePreviousDate(CdrFilesGroupId unreceivedSeqObj, int sequence, CdrFilesTitleConfiguration cdrConfig){
        try {
            CdrFilesGroupId cdrFilesGroupId = this.cdrFilesGroupIdRepo.findBySeqBetweenMaxAndMin(sequence, cdrConfig.getCdrType(), cdrConfig.getCdrGroup(), cdrConfig.getCategory(), cdrConfig.getNoeud())
                    .orElse(new CdrFilesGroupId());
            if (cdrFilesGroupId.getId() != null) {
                int previousUnreceived = cdrFilesGroupId.getUnreceivedSeqSet().size();
                List<Integer> unreceivedSeq = cdrFilesGroupId.getUnreceivedSeqSet();
                unreceivedSeq.remove(Integer.valueOf(sequence));
                cdrFilesGroupId.setUnreceivedSeqSet(unreceivedSeq);
                this.cdrFilesGroupIdRepo.save(cdrFilesGroupId);
                this.statService.updatePreviousDate(cdrFilesGroupId, cdrConfig.getCdrName());
            } else {
                throw new NoDateContinuesSequenceException("Can't find date of sequence " + sequence);
            }
        } catch (NoDateContinuesSequenceException e) {
            log.error("No date continues sequence", e);
        }
    }
}
