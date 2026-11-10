package com.atlasglobal.integration.bcb;

import com.atlasglobal.ingestion.ProviderData;
import com.atlasglobal.policyrate.PolicyRateData;
import com.atlasglobal.policyrate.PolicyRateProvider;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BcbPolicyRateProvider implements PolicyRateProvider {
    private final BcbClient client;
    BcbPolicyRateProvider(BcbClient client) { this.client = client; }
    public ProviderData.Source source() { return BcbCatalog.SOURCE; }
    public long lockKey() { return 728301952150L; }
    public List<PolicyRateData.Series> load(LocalDate from, LocalDate to) {
        return List.of(new PolicyRateData.Series("BR", "432", "Meta Selic definida pelo Copom",
            "Daily Selic target set by Copom, in percent per year. This is the policy target, not the effective overnight Selic rate. "
                + "Reference dates and the published daily frequency are retained; no annual averaging or gap filling is performed. "
                + "https://dadosabertos.bcb.gov.br/dataset/432-taxa-de-juros---meta-selic-definida-pelo-copom",
            "Banco Central do Brasil — Copom / Demab", client.policyRate(from, to)));
    }
}
