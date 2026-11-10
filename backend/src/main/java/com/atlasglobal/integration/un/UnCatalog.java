package com.atlasglobal.integration.un;

import com.atlasglobal.ingestion.ProviderData;
import java.net.URI;
import java.util.List;

/** Published UNDP composite indices keep their report edition and original scale. */
final class UnCatalog {
    private UnCatalog() {}
    static final URI DOCUMENTATION = URI.create("https://hdr.undp.org/data-center/documentation-and-downloads");
    static final ProviderData.Source SOURCE = new ProviderData.Source("UNDP", "United Nations Development Programme",
        "https://hdr.undp.org/", "Official Human Development Report composite indices", DOCUMENTATION.toString());

    record Entry(String code, String name, String prefix, String category, String originalName, String description) {
        ProviderData.Series definition(int edition) {
            return new ProviderData.Series(code, name, category, "INDEX_0_1", "HDR:" + edition);
        }
        ProviderData.Indicator metadata(int edition, int latestReference) {
            return new ProviderData.Indicator(prefix, originalName,
                description + " Published by UNDP in Human Development Report " + edition
                    + "; the latest reference year in this release is " + latestReference + ". "
                    + "These are official composite indices, which can incorporate source estimates and revisions; "
                    + "Atlas Global does not calculate or interpolate them. The report edition is separate from "
                    + "the observation year. Compare the same edition; revisions can change historical values. "
                    + "Unknown statistical revision dates remain unavailable. Technical documentation: "
                    + "https://hdr.undp.org/data-center/documentation-and-downloads",
                "UNDP Human Development Report Office");
        }
    }

    static final List<Entry> ENTRIES = List.of(
        new Entry("UNDP.HDI", "Índice de Desenvolvimento Humano (IDH)", "hdi", "DEMOGRAPHY",
            "Human Development Index (HDI)",
            "Index on the original 0–1 scale. The geometric mean of normalized health, education, and gross "
                + "national income dimension indices; it does not directly measure inequality or all aspects of wellbeing."),
        new Entry("UNDP.IHDI", "IDH ajustado à desigualdade", "ihdi", "INEQUALITY",
            "Inequality-adjusted Human Development Index (IHDI)",
            "Index on the original 0–1 scale. Human development dimensions are discounted for inequality in "
                + "their distributions. Survey periods and coverage can differ; availability is narrower than HDI. "
                + "This is neither a Gini coefficient nor the percentage loss due to inequality.")
    );
}
