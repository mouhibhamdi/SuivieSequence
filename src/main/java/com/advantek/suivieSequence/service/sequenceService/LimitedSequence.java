package com.advantek.suivieSequence.service.sequenceService;

import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.model.SequenceObj;
import com.advantek.suivieSequence.repository.CdrFilesGroupIdRepo;
import com.advantek.suivieSequence.service.StatCdrFilesService;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LimitedSequence {
    private final CdrFilesGroupIdRepo cdrFilesGroupIdRepo;
    private final StatCdrFilesService statService;

    public LimitedSequence(CdrFilesGroupIdRepo cdrFilesGroupIdRepo, StatCdrFilesService statService) {
        this.cdrFilesGroupIdRepo = cdrFilesGroupIdRepo;
        this.statService = statService;
    }

    public void suivieLimitedSeq(CdrFilesTitleConfiguration cdrConfig, LocalDate localDate) {
        log.info("CDR type {} group {}", cdrConfig.getCdrType(), cdrConfig.getCdrGroup());
        LocalDate startDate = this.cdrFilesGroupIdRepo
                .readRecentDate(cdrConfig.getCdrType(), cdrConfig.getCdrGroup(), cdrConfig.getCategory(), cdrConfig.getNoeud())
                .orElse(new CdrFilesGroupId(cdrConfig.getCdrType(), cdrConfig.getCdrGroup(), cdrConfig.getNoeud(), cdrConfig.getCategory(), localDate.minusDays(1))).getDate().plusDays(1);
        List<SequenceObj> sequenceObjList = SequenceListUtils.getLimitedSeqList(cdrConfig, localDate, startDate);
        log.info("sequence liste size {}", sequenceObjList.size() );
        if(!sequenceObjList.isEmpty()) {
            Map<LocalDate, List<SequenceObj>> map = sequenceObjList.stream()
                    .collect(Collectors.groupingBy(SequenceObj::getDate));

            for (LocalDate date : map.keySet()) {
                log.info("Sequencing of the date {}", date);
                CdrFilesGroupId unreceivedSeqObj = this.cdrFilesGroupIdRepo
                        .findByCdrTypeAndCdrGroupAndCategoryAndNoeudAndDate(cdrConfig.getCdrType(), cdrConfig.getCdrGroup(), cdrConfig.getCategory(), cdrConfig.getNoeud(), date)
                        .orElse(new CdrFilesGroupId(cdrConfig.getCdrType(), cdrConfig.getCdrGroup(), cdrConfig.getCategory(), cdrConfig.getNoeud(), date));
                log.info(
                        "\nCdrFilesGroupId ==> \nID: {}; \nDate: {}; \nCDR Type: {}; \nCDR Group: {}; \nCategory: {}; \nMAX: {}; \nMin: {}; \nUnreceived size: {}",
                        unreceivedSeqObj.getId(), unreceivedSeqObj.getDate(), unreceivedSeqObj.getCdrType(), unreceivedSeqObj.getCdrGroup(), unreceivedSeqObj.getCategory(), unreceivedSeqObj.getMax(), unreceivedSeqObj.getMin(), unreceivedSeqObj.getUnreceivedSeqSet().size()
                );
                int previousUnreceived = unreceivedSeqObj.getUnreceivedSeqSet().size();

                List<Integer> newReceivedSeq = new ArrayList<>(map.get(date).stream().mapToInt(SequenceObj::getSequence).boxed().toList());
                Collections.sort(newReceivedSeq);
                int newMaxReceivedSeq = newReceivedSeq.get(newReceivedSeq.size() - 1);
                List<Integer> unreceivedSeqSet = unreceivedSeqObj.getUnreceivedSeqSet();
                if (newMaxReceivedSeq > unreceivedSeqObj.getMax()) {
                    for (int i = unreceivedSeqObj.getMax() + 1; i <= newMaxReceivedSeq; i++) {
                        unreceivedSeqSet.add(i);
                    }
                    unreceivedSeqObj.setMax(newMaxReceivedSeq);
                }
                unreceivedSeqSet.removeAll(newReceivedSeq);
                unreceivedSeqObj.setUnreceivedSeqSet(unreceivedSeqSet);
                log.info(
                        "\nEND CdrFilesGroupId ==> ID: {}; Date: {}; CDR Type: {}; CDR Group: {}; Category: {}; MAX: {}; Min: {}; Unreceived size: {}",
                        unreceivedSeqObj.getId(), unreceivedSeqObj.getDate(), unreceivedSeqObj.getCdrType(), unreceivedSeqObj.getCdrGroup(), unreceivedSeqObj.getCategory(), unreceivedSeqObj.getMax(), unreceivedSeqObj.getMin(), unreceivedSeqObj.getUnreceivedSeqSet().size()
                );
                this.cdrFilesGroupIdRepo.save(unreceivedSeqObj);
                this.statService.updateCdrStat(unreceivedSeqObj, cdrConfig.getCdrName(), previousUnreceived, newReceivedSeq.size());
            }
        }
//        else {
//            this.statService.calculEstimateNotReceived(cdrConfig.getCdrName());
//        }
    }
}
