package com.atlasglobal.shared;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebConfigurationTest {
    @Test
    void rejectsWildcardOrigins() {
        assertThatThrownBy(() -> new WebConfiguration.CorsProperties(List.of("*")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAllowsConfiguredOriginAndReadMethods() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfiguration.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(options("/api/probe").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
            mvc.perform(options("/api/probe").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
            mvc.perform(options("/api/probe").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        }
    }

    @Configuration
    @EnableWebMvc
    static class TestConfiguration {
        @Bean WebConfiguration webConfiguration() {
            return new WebConfiguration(new WebConfiguration.CorsProperties(List.of("http://localhost:5173")));
        }
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/probe") String probe() { return "ok"; }
    }
}
