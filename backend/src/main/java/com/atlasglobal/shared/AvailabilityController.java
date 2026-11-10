package com.atlasglobal.shared;

import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cheap platform probe; the private Actuator readiness group checks PostgreSQL. */
@RestController
public class AvailabilityController {
    private final ApplicationAvailability availability;

    public AvailabilityController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> health() {
        boolean ready = availability.getLivenessState() == LivenessState.CORRECT
            && availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .cacheControl(CacheControl.noStore())
            .body(Map.of("status", ready ? "UP" : "DOWN"));
    }
}
