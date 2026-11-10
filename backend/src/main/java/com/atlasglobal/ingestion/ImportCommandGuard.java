package com.atlasglobal.ingestion;

import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Rejects conflicting operational commands before any ApplicationRunner can execute. */
@Component
class ImportCommandGuard {
    ImportCommandGuard(@Value("${atlas.import.run-once:false}") boolean worldBank,
                       @Value("${atlas.oecd-import.run-once:false}") boolean oecd,
                       @Value("${atlas.ibge-import.run-once:false}") boolean ibge,
                       @Value("${atlas.bcb-import.run-once:false}") boolean bcb,
                       @Value("${atlas.policy-rates.run-once:false}") boolean policyRates) {
        if (Stream.of(worldBank, oecd, ibge, bcb, policyRates).filter(Boolean::booleanValue).count() > 1) {
            throw new IllegalArgumentException("Run one provider import command at a time");
        }
    }
}
