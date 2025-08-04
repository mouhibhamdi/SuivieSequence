package com.advantek.suivieSequence.runners;

import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.repository.CdrFilesTitleConfigurationRepo;
import com.advantek.suivieSequence.service.sequenceService.ContinuesSequence;
import com.advantek.suivieSequence.service.sequenceService.LimitedSequence;
import com.advantek.suivieSequence.service.sequenceService.NoDateContinuesSequence;
import com.advantek.suivieSequence.service.sequenceService.SuivieMobileMoney;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class CdrFilesSequencerEntry implements CommandLineRunner {
    @Value("${numberDays}")
    private int numberDays;

    private final LimitedSequence limitedSequence;
    private final ContinuesSequence continuesSequence;
    private final NoDateContinuesSequence noDateContinuesSequence;
    private final SuivieMobileMoney suivieMobileMoney;
    private final CdrFilesTitleConfigurationRepo cdrFilesConfig;

    public CdrFilesSequencerEntry(LimitedSequence limitedSequence, ContinuesSequence continuesSequence, NoDateContinuesSequence noDateContinuesSequence, SuivieMobileMoney suivieMobileMoney, CdrFilesTitleConfigurationRepo cdrFilesConfig) {
        this.limitedSequence = limitedSequence;
        this.continuesSequence = continuesSequence;
        this.noDateContinuesSequence = noDateContinuesSequence;
        this.suivieMobileMoney = suivieMobileMoney;
        this.cdrFilesConfig = cdrFilesConfig;
    }

    @Override
    public void run(String... args) throws Exception {
        List<CdrFilesTitleConfiguration> cdrFilesConfigList = this.cdrFilesConfig.findAll();
        LocalDate processDate = LocalDate.now().minusDays(numberDays);
        LocalDate endDate = LocalDate.now();
        while (processDate.isBefore(endDate)) {
            for (CdrFilesTitleConfiguration cdrFileConfig : cdrFilesConfigList) {
                try {
                    log.info("Start read sequences for CDR type {} CDR group {}", cdrFileConfig.getCdrType(), cdrFileConfig.getCdrGroup());
                    if (cdrFileConfig.isNoDateContinuesSequence()) {
                        this.noDateContinuesSequence.suivieNoDateContinuesSeq(cdrFileConfig, processDate);
                    } else if (cdrFileConfig.isContinuesSequence()) {
                        this.continuesSequence.suivieContinuesSeq(cdrFileConfig, processDate);
                    } else {
                        if (cdrFileConfig.getCdrName().contains("MM_")) {
                            this.suivieMobileMoney.suivieMM(cdrFileConfig, processDate);
                        } else {
                            this.limitedSequence.suivieLimitedSeq(cdrFileConfig, processDate);
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            processDate = processDate.plusDays(1);
        }
    }
}
