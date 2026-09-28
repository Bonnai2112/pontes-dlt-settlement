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
    void issuesADigitalBondOnEachPlatform() {
        var bondOnA = holding("BANKBFRPP", "XS0000000001");
        assertThat(bondOnA.available() + bondOnA.locked()).isEqualTo(1_000);
        var bondOnB = holding(marketDlts.platform(MARKET_DLT_B), "BANKCDEFF", "XS0000000002");
        assertThat(bondOnB.available() + bondOnB.locked()).isEqualTo(500);
    }

    @Test
    void onboardsAndLinksTheDemoBanksOnBothPlatforms() {
        for (String platform : new String[] {MARKET_DLT_A, MARKET_DLT_B}) {
            assertThat(marketDlts.platform(platform).participants()).extracting(p -> p.party())
                    .contains("BANKAFRPP", "BANKBFRPP", "BANKCDEFF");
            assertThat(pontesReferenceData.platforms()).filteredOn(p -> p.platform().equals(platform))
                    .singleElement().satisfies(p -> assertThat(p.participants())
                            .contains("BANKAFRPP", "BANKBFRPP", "BANKCDEFF"));
        }
    }
}
