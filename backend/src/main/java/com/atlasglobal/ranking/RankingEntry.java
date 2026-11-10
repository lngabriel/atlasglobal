package com.atlasglobal.ranking;

import com.atlasglobal.observation.CountryReference;
import java.math.BigDecimal;

public record RankingEntry(long rank, CountryReference country, BigDecimal value, String providerStatus) {}
