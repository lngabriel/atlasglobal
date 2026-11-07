package com.atlasglobal.ingestion;

import java.util.List;
import java.util.function.Consumer;

public interface DataProvider {
    String code();
    List<ProviderData.Country> countries();
    ProviderData.Indicator indicator(String code);
    void observations(List<String> countries, String indicator, int from, int to,
                      Consumer<ProviderData.Batch> consumer);
}
