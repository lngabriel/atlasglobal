package com.atlasglobal.policyrate;

import com.atlasglobal.ingestion.ProviderData;
import java.time.LocalDate;
import java.util.List;

public interface PolicyRateProvider {
    ProviderData.Source source();
    long lockKey();
    List<PolicyRateData.Series> load(LocalDate from, LocalDate to);
}
