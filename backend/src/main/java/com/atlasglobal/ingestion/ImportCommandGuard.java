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
                       @Value("${atlas.policy-rates.run-once:false}") boolean policyRates,
                       @Value("${atlas.imf-import.run-once:false}") boolean imf,
                       @Value("${atlas.un-import.run-once:false}") boolean un,
                       @Value("${atlas.eurostat-import.run-once:false}") boolean eurostat) {
        if (Stream.of(worldBank, oecd, ibge, bcb, policyRates, imf, un, eurostat).filter(Boolean::booleanValue).count() > 1) {
            throw new IllegalArgumentException("Run one provider import command at a time");
        }
    }
}
