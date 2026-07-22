package com.andresblue.threatdirector.api.v1;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationApiContractTest {
    @Test
    void apiVersionAndEntrypointAreStable() {
        assertEquals(1, ThreatDirectorApi.API_VERSION);
        assertEquals("threatdirector-api-v1", ThreatDirectorApi.ENTRYPOINT_KEY);
        assertEquals(1, new ThreatDirectorApiProvider().apiVersion());
    }

    @Test
    void strategicContextIsBoundedAndDeterministic() {
        StrategicThreatContext context = new StrategicThreatContext(2, -.5, .5, .5, 1);
        assertEquals(.6, context.combined(), 1.0e-9);
    }

    @Test
    void requestDefensivelyCopiesIdentitySets() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        EncounterRequest request = new EncounterRequest(
                source, "iron_covenant", "SCOUT_REGION", Set.of(target), "overworld:0:0",
                Optional.empty(), Optional.empty(), .8, PressureProfile.HIGH,
                Set.of("illager"), Set.of(), 8, true, false, false, true,
                new StrategicThreatContext(.1, .2, .3, .4, .5));
        assertEquals(Set.of(target), request.targetPlayers());
        assertThrows(UnsupportedOperationException.class,
                () -> request.targetPlayers().add(UUID.randomUUID()));
    }
}
