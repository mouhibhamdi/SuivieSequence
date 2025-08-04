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

    public void updateCdrStat(CdrFilesGroupId unreceivedSequence, String cdrName, int previousUnreceived, int newSequenceSize){
        CdrFilesSurvey stat = this.statCdrFilesRepo.findByDateAndType(unreceivedSequence.getDate(), cdrName)
                .orElse(new CdrFilesSurvey(unreceivedSequence.getDate(), cdrName));
        int newUnreceived = unreceivedSequence.getUnreceivedSeqSet().size();
        stat.setMissing(calculMonquant(stat.getMissing().intValue(), previousUnreceived, newUnreceived));
        stat.addCount(newSequenceSize);

        this.statCdrFilesRepo.save(stat);
    }

    public void updatePreviousDate(CdrFilesGroupId unreceivedSequence, String cdrName) {
        CdrFilesSurvey stat = this.statCdrFilesRepo.findByDateAndType(unreceivedSequence.getDate(), cdrName)
                .orElse(new CdrFilesSurvey(unreceivedSequence.getDate(), cdrName));

        stat.addCount(1);
        stat.subtractMissing(1);
    }

    public void updateNoDateRecivedLate(CdrFilesGroupId cdrFilesGroupId, String cdrName, int size){
        CdrFilesSurvey stat = this.statCdrFilesRepo.findByDateAndType(cdrFilesGroupId.getDate(), cdrName)
                .orElse(new CdrFilesSurvey(cdrFilesGroupId.getDate(), cdrName));

        stat.addCount(size);
        stat.subtractMissing(size);
    }

    public CdrFilesSurvey getCdrFileSurveyByDateAndName (LocalDate date, String cdrName){
        return this.statCdrFilesRepo.findByDateAndType(date, cdrName)
                .orElse(new CdrFilesSurvey(date, cdrName));
    }

    public void saveCdrState(CdrFilesSurvey cdrFilesSurvey){
        this.statCdrFilesRepo.save(cdrFilesSurvey);
    }

    public LocalDate getLastExecutionDate(String cdrType, LocalDate yesterday){
        return this.statCdrFilesRepo.readRecentDate(cdrType)
                .orElse(new CdrFilesSurvey(yesterday.minusDays(2), cdrType))
                .getDate();
    }

    public void calculEstimateNotReceived(String cdrName){
        List<CdrFilesSurvey> last30Day = this.statCdrFilesRepo.readLastValues(cdrName);
        Estimation estimation = new Estimation(cdrName, last30Day);
        List<BigDecimal> interval = estimation.getEstimationInterval(3);
        CdrFilesSurvey cdrFilesSurvey = new CdrFilesSurvey(LocalDate.now().minusDays(1), cdrName);
//        cdrFilesSurvey.setEstimationInf(interval.get(0));
//        cdrFilesSurvey.setEstimationSup(interval.get(1));
        this.statCdrFilesRepo.save(cdrFilesSurvey);
    }

    private BigDecimal calculMonquant(int value, int valueToRemove, int valueToAdd){
        return BigDecimal.valueOf(value - valueToRemove + valueToAdd);
    }
}
