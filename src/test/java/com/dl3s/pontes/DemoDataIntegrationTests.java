package com.dl3s.pontes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoDataIntegrationTests extends PontesIntegrationTest {

    @Test
    void opensDemoRtgsAccounts() {
        assertThat(rtgs.get(technicalAccount).balance()).isNotNull();
        assertThat(rtgsBalance("BANKAFRPP")).isEqualByComparingTo("10000000");
        assertThat(rtgsBalance("BANKCDEFF")).isEqualByComparingTo("10000000");
        assertThat(rtgsBalance("BANKBFRPP")).isEqualByComparingTo("5000000");
    }

    @Test
    void issuesDigitalBondForBankB() {
        var bond = holding("BANKBFRPP", "XS0000000001");
        assertThat(bond.available() + bond.locked()).isEqualTo(1_000);
    }
}
