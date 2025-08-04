package com.advantek.suivieSequence.service;

import com.advantek.suivieSequence.entity.CdrFilesTitleConfiguration;
import com.advantek.suivieSequence.repository.CdrFilesTitleConfigurationRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CDRFilesConfigService {
    private final CdrFilesTitleConfigurationRepo cdrFilesConfigRepo;

    public CDRFilesConfigService(CdrFilesTitleConfigurationRepo cdrFilesConfigRepo) {
        this.cdrFilesConfigRepo = cdrFilesConfigRepo;
    }

    public List<CdrFilesTitleConfiguration> getCDRFilesConfig(){
        return this.cdrFilesConfigRepo.findAll();
    }
}
