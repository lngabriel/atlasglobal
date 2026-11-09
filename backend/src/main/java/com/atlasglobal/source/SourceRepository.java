package com.atlasglobal.source;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceRepository extends JpaRepository<DataSource, UUID> {
    Optional<DataSource> findByCode(String code);

    @Query("""
        SELECT source FROM DataSource source
        WHERE :q IS NULL OR lower(source.name) LIKE :q ESCAPE '!'
            OR lower(source.code) LIKE :q ESCAPE '!'
        """)
    Page<DataSource> search(@Param("q") String query, Pageable pageable);
}
