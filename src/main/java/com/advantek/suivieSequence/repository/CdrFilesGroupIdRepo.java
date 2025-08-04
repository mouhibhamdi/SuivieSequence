package com.advantek.suivieSequence.repository;

import com.advantek.suivieSequence.entity.CdrFilesGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CdrFilesGroupIdRepo extends JpaRepository<CdrFilesGroupId, Long> {
    Optional<CdrFilesGroupId> findByCdrTypeAndCdrGroupAndCategoryAndNoeudAndDate(String cdrType, String cdrGroup, String category, String noeud, LocalDate date);

    @Query("SELECT e FROM CdrFilesGroupId e WHERE e.cdrType = :cdrType AND e.cdrGroup = :cdrGroup AND e.category = :category AND e.noeud = :noeud ORDER BY e.date DESC LIMIT 1")
    Optional<CdrFilesGroupId> readRecentDate(String cdrType, String cdrGroup, String category, String noeud);

    @Query("SELECT e FROM CdrFilesGroupId e WHERE :seq < e.max AND :seq >= e.min AND e.cdrType = :cdrType AND e.cdrGroup = :cdrGroup AND e.category = :category AND e.noeud = :noeud ORDER BY e.date DESC LIMIT 1")
    Optional<CdrFilesGroupId> findBySeqBetweenMaxAndMin(@Param("seq") int seq, String cdrType, String cdrGroup, String category, String noeud);
}
