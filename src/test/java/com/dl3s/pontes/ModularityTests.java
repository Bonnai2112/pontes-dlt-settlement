package com.dl3s.pontes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Verifies the bounded context boundaries and generates the documentation in target/spring-modulith-docs. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PontesApplication.class);

    @Test
    void respectsInterModuleDependencyRules() {
        modules.verify();
    }

    @Test
    void detectsExpectedBoundedContexts() {
        assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("rtgs", "trigger", "cashtoken", "marketdlt", "interop", "demo");
    }

    @Test
    void generatesDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases()
                .writeAggregatingDocument();
    }
}
