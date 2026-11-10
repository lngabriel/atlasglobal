package com.atlasglobal.ingestion;

import java.util.List;

/** Annual source slices normalized before they reach the shared import store. */
public interface AnnualDataProvider {
    ProviderData.Source source();
    long lockKey();
    List<ProviderData.ImportedSeries> load(ImportProperties settings);
}
