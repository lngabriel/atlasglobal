package com.atlasglobal.indicator;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IndicatorRepository extends JpaRepository<Indicator, UUID> {
    Optional<Indicator> findByCode(String code);

    @Query("""
        SELECT i FROM Indicator i
        WHERE (:q IS NULL OR LOWER(i.name) LIKE :q ESCAPE '!' OR LOWER(i.code) LIKE :q ESCAPE '!')
          AND (:category IS NULL OR i.category = :category)
          AND (:source IS NULL OR EXISTS (
            SELECT s.id FROM IndicatorSeries s, DataSource d
            WHERE s.indicatorId = i.id AND s.dataSourceId = d.id AND LOWER(d.code) = :source))
        """)
    Page<Indicator> search(@Param("q") String q, @Param("category") String category,
                           @Param("source") String source, Pageable page);
}
