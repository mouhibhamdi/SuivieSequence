package com.advantek.suivieSequence.service.sequenceService;

import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.service.StatCdrFilesService;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class SuivieMobileMoney {

    private final StatCdrFilesService statCdrFilesService;

    public SuivieMobileMoney(StatCdrFilesService statCdrFilesService) {
        this.statCdrFilesService = statCdrFilesService;
    }

    public void suivieMM(CdrFilesTitleConfiguration cdrFileConfig, LocalDate yesterday) {
        // Détermine la dernière date valide à partir du repo
        LocalDate lastValidSituationDate = this.statCdrFilesService
                .getLastExecutionDate(cdrFileConfig.getCdrName(), yesterday)
                .plusDays(2);

        // Récupère toutes les dates de fichiers Mobile Money
        List<LocalDate> mmFiles = SequenceListUtils.getDateFiles(cdrFileConfig, lastValidSituationDate, yesterday);
        log.info("sequence list size {}", mmFiles.size());

        mmFiles.sort(LocalDate::compareTo);

        // Boucle sur chaque date pour mettre à jour les stats
        LocalDate iterationDate = lastValidSituationDate.plusDays(1);
        while (iterationDate.isBefore(yesterday.plusDays(1))) {
            CdrFilesGroupId fakeGroupId = new CdrFilesGroupId();
            fakeGroupId.setDate(iterationDate);

            if (mmFiles.contains(iterationDate)) {
                log.info("File with date {} exists", iterationDate);
                // Ajoute un fichier reçu pour cette date
                statCdrFilesService.updateNoDateRecivedLate(fakeGroupId, cdrFileConfig.getCdrName(), 1);
                mmFiles.remove(iterationDate);
            } else {
                log.warn("File with date {} does not exist", iterationDate);
                // Ajoute un missing pour cette date
                statCdrFilesService.updateNoDateRecivedLate(fakeGroupId, cdrFileConfig.getCdrName(), -1);
            }

            iterationDate = iterationDate.plusDays(1);
        }

        log.info("{} date(s) to be updated", mmFiles.size());

        // Cas particulier : aucune date restante → ajoute un missing pour la veille
        if (mmFiles.isEmpty()) {
            CdrFilesGroupId fakeGroupId = new CdrFilesGroupId();
            fakeGroupId.setDate(yesterday.minusDays(1));
            statCdrFilesService.updateNoDateRecivedLate(fakeGroupId, cdrFileConfig.getCdrName(), -1);
        }

        // Met à jour les fichiers restants comme "reçus"
        for (LocalDate updateDate : mmFiles) {
            CdrFilesGroupId fakeGroupId = new CdrFilesGroupId();
            fakeGroupId.setDate(updateDate);
            log.info("update missing date {}", updateDate);
            statCdrFilesService.updateNoDateRecivedLate(fakeGroupId, cdrFileConfig.getCdrName(), 1);
        }
    }
}
