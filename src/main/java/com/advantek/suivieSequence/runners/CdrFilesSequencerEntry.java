package com.advantek.suivieSequence.runners;

import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.repository.CdrFilesTitleConfigurationRepo;
import com.advantek.suivieSequence.service.sequenceService.ContinuesSequence;
import com.advantek.suivieSequence.service.sequenceService.LimitedSequence;
import com.advantek.suivieSequence.service.sequenceService.NoDateContinuesSequence;
import com.advantek.suivieSequence.service.sequenceService.SuivieMobileMoney;
import com.advantek.suivieSequence.utils.SequenceListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class CdrFilesSequencerEntry implements CommandLineRunner {

    private final LimitedSequence limitedSequence;
    private final ContinuesSequence continuesSequence;
    private final NoDateContinuesSequence noDateContinuesSequence;
    private final SuivieMobileMoney suivieMobileMoney;
    private final CdrFilesTitleConfigurationRepo cdrFilesConfig;

    public CdrFilesSequencerEntry(
            LimitedSequence limitedSequence,
            ContinuesSequence continuesSequence,
            NoDateContinuesSequence noDateContinuesSequence,
            SuivieMobileMoney suivieMobileMoney,
            CdrFilesTitleConfigurationRepo cdrFilesConfig) {

        this.limitedSequence = limitedSequence;
        this.continuesSequence = continuesSequence;
        this.noDateContinuesSequence = noDateContinuesSequence;
        this.suivieMobileMoney = suivieMobileMoney;
        this.cdrFilesConfig = cdrFilesConfig;
    }

    @Override
    public void run(String... args) throws Exception {

        List<CdrFilesTitleConfiguration> configs = this.cdrFilesConfig.findAll();

        // Séparer les configs "continuesSequence" des autres
        List<CdrFilesTitleConfiguration> continuesConfigs = configs.stream()
                .filter(CdrFilesTitleConfiguration::isContinuesSequence)
                .toList();

        List<CdrFilesTitleConfiguration> otherConfigs = configs.stream()
                .filter(cfg -> !cfg.isContinuesSequence())
                .toList();

        // 1) Traiter toutes les configs continues en une seule passe
        if (!continuesConfigs.isEmpty()) {
            LocalDate endDate = continuesConfigs.stream()
                    .map(cfg -> SequenceListUtils.getLastExistingDateForConfiguration(cfg)
                            .orElse(LocalDate.now()))
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.now());

            log.info("Start ContinuesSequence GLOBAL from earliest to latest date: {}", endDate);
            continuesSequence.suivieContinuesSeq(continuesConfigs, endDate);
        }

        // 2) Traiter les autres configs de façon journalière
        for (CdrFilesTitleConfiguration cfg : otherConfigs) {

            try {
                log.info("Processing config: {} ({})", cfg.getCdrName(), cfg.getCdrType());

                LocalDate startDate = SequenceListUtils.getFirstExistingDateForConfiguration(cfg)
                        .orElseThrow(() -> new IllegalStateException("No files found for " + cfg.getCdrName()));
                LocalDate endDate = SequenceListUtils.getLastExistingDateForConfiguration(cfg)
                        .orElse(startDate);

                LocalDate processDate = startDate;
                while (!processDate.isAfter(endDate)) {
                    try {
                        log.info("Start daily read for {} at date {}", cfg.getCdrName(), processDate);

                        if (cfg.isNoDateContinuesSequence()) {
                            noDateContinuesSequence.suivieNoDateContinuesSeq(cfg, processDate);
                        } else if (cfg.getCdrName().contains("MM_")) {
                            suivieMobileMoney.suivieMM(cfg, processDate);
                        } else {
                            limitedSequence.suivieLimitedSeq(cfg, processDate);
                        }

                    } catch (Exception e) {
                        log.error("Error for config {} at {}", cfg.getCdrName(), processDate, e);
                    }

                    processDate = processDate.plusDays(1);
                }

            } catch (Exception e) {
                log.error("Error processing config {}", cfg.getCdrName(), e);
            }
        }
    }
}
