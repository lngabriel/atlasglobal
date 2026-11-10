package com.atlasglobal.shared;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvailabilityControllerTest {
    private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(availability))
        .addFilters(new ResponseHeadersFilter()).build();

    @Test
    void exposesOnlyUncachedAvailabilityWithoutDatasourceAccessOrInternalDetails() throws Exception {
        when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        mvc.perform(get("/healthz"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}", true))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
        mvc.perform(head("/healthz")).andExpect(status().isOk());
        mvc.perform(post("/healthz")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void returnsUnavailableDuringStartupOrGracefulShutdown() throws Exception {
        when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/healthz")).andExpect(status().isServiceUnavailable())
            .andExpect(content().json("{\"status\":\"DOWN\"}", true));
        when(availability.getLivenessState()).thenReturn(LivenessState.BROKEN);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        mvc.perform(get("/healthz")).andExpect(status().isServiceUnavailable());
    }
}
