package com.advantek.suivieSequence.service;

import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import com.advantek.suivieSequence.entity.CdrFilesSurvey;
import com.advantek.suivieSequence.model.Estimation;
import com.advantek.suivieSequence.repository.StatCdrFilesRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class StatCdrFilesService {

    private final StatCdrFilesRepo statCdrFilesRepo;

    public StatCdrFilesService(StatCdrFilesRepo statCdrFilesRepo) {
        this.statCdrFilesRepo = statCdrFilesRepo;
    }

    // ---------------------------------------------------------------------
    // UPDATE STAT FOR NEW DAY
    // ---------------------------------------------------------------------

    public void updateCdrStat(
            CdrFilesGroupId unreceivedSequence,
            String cdrName,
            int previousUnreceived,
            int newSequenceSize) {

        int newUnreceived = unreceivedSequence.getUnreceivedSeqSet().size();

        BigDecimal currentMissing = BigDecimal.valueOf(
                calculManquant(previousUnreceived, previousUnreceived, newUnreceived)
        );

        statCdrFilesRepo.upsert(
                unreceivedSequence.getDate(),
                cdrName,
                BigDecimal.valueOf(newSequenceSize),
                currentMissing
        );
    }

    // ---------------------------------------------------------------------
    // UPDATE FOR FILE RECEIVED LATE (DAY-1 OR OLDER)
    // ---------------------------------------------------------------------

    public void updatePreviousDate(CdrFilesGroupId unreceivedSequence, String cdrName) {

        statCdrFilesRepo.upsert(
                unreceivedSequence.getDate(),
                cdrName,
                BigDecimal.valueOf(1),    // addCount(1)
                BigDecimal.valueOf(-1)    // subtractMissing(1)
        );
    }

    // ---------------------------------------------------------------------
    // UPDATE FOR NO-DATE CONTINUES SEQ (ex: Mobile Money)
    // ---------------------------------------------------------------------

    public void updateNoDateRecivedLate(CdrFilesGroupId cdrFilesGroupId, String cdrName, int size) {

        statCdrFilesRepo.upsert(
                cdrFilesGroupId.getDate(),
                cdrName,
                BigDecimal.valueOf(size),
                BigDecimal.valueOf(-size)
        );
    }

    // ---------------------------------------------------------------------
    // GET LAST EXECUTION DATE
    // ---------------------------------------------------------------------

    public LocalDate getLastExecutionDate(String cdrType, LocalDate yesterday) {
        return statCdrFilesRepo
                .findFirstByTypeOrderByDateDesc(cdrType)
                .orElse(new CdrFilesSurvey(yesterday.minusDays(2), cdrType))
                .getDate();
    }

    // ---------------------------------------------------------------------
    // ESTIMATIONS (NOT USED CURRENTLY)
    // ---------------------------------------------------------------------

    public void calculEstimateNotReceived(String cdrName) {

        List<CdrFilesSurvey> last30Day = statCdrFilesRepo.findTop30ByTypeOrderByDateDesc(cdrName);

        Estimation estimation = new Estimation(cdrName, last30Day);

        List<BigDecimal> interval = estimation.getEstimationInterval(3);

        // Création d'une nouvelle entrée pour estimation (désactivée volontairement)
        CdrFilesSurvey cdrFilesSurvey = new CdrFilesSurvey(LocalDate.now().minusDays(1), cdrName);

        statCdrFilesRepo.upsert(
                cdrFilesSurvey.getDate(),
                cdrName,
                BigDecimal.ZERO,  // pas de count réel
                BigDecimal.ZERO   // pas de missing réel
        );
    }

    // ---------------------------------------------------------------------
    // UTIL
    // ---------------------------------------------------------------------

    private int calculManquant(int value, int valueToRemove, int valueToAdd) {
        return value - valueToRemove + valueToAdd;
    }
}
