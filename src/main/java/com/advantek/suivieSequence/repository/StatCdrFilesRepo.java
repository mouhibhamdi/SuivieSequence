package com.advantek.suivieSequence.repository;

import com.advantek.suivieSequence.entity.CdrFilesSurvey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StatCdrFilesRepo extends JpaRepository<CdrFilesSurvey, Long> {
    Optional<CdrFilesSurvey> findByDateAndType(LocalDate date, String type);

    @Query("SELECT e FROM CdrFilesSurvey e WHERE e.type = :type ORDER BY e.date DESC LIMIT 1")
    Optional<CdrFilesSurvey> readRecentDate(String type);

    @Query("SELECT e FROM CdrFilesSurvey e WHERE e.type = :type ORDER BY e.date DESC LIMIT 30")
    List<CdrFilesSurvey> readLastValues(String type);
}
