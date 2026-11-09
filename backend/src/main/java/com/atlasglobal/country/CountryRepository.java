package com.atlasglobal.country;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryRepository extends JpaRepository<Country, UUID> {
    Optional<Country> findByIso3Code(String iso3Code);

    @Query("""
        SELECT country FROM Country country
        WHERE (:q IS NULL OR lower(country.name) LIKE :q ESCAPE '!'
            OR lower(country.originalName) LIKE :q ESCAPE '!'
            OR lower(country.iso2Code) LIKE :q ESCAPE '!'
            OR lower(country.iso3Code) LIKE :q ESCAPE '!'
            OR lower(country.region) LIKE :q ESCAPE '!')
          AND (:region IS NULL OR lower(country.region) = :region)
          AND (:incomeLevel IS NULL OR lower(country.incomeLevel) = :incomeLevel)
        """)
    Page<Country> search(@Param("q") String query, @Param("region") String region,
                         @Param("incomeLevel") String incomeLevel, Pageable pageable);
}
