package com.advantek.suivieSequence.repository;

import com.advantek.suivieSequence.entity.CdrFilesSurvey;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StatCdrFilesRepo extends JpaRepository<CdrFilesSurvey, Long> {

    Optional<CdrFilesSurvey> findByDateAndType(LocalDate date, String type);

    Optional<CdrFilesSurvey> findByDateAndTypeAndNode(LocalDate date, String type, String node);

    Optional<CdrFilesSurvey> findFirstByTypeOrderByDateDesc(String type);

    List<CdrFilesSurvey> findTop30ByTypeOrderByDateDesc(String type);

    // UPSERT CUMULATIF
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cdr_files_survey (date, type, count, missing)
            VALUES (:date, :type, :count, :missing)
            ON CONFLICT (date, type)
            DO UPDATE SET
                count = cdr_files_survey.count + EXCLUDED.count,
                missing = cdr_files_survey.missing + EXCLUDED.missing
            """, nativeQuery = true)
    void upsert(LocalDate date, String type, BigDecimal count, BigDecimal missing);
}
