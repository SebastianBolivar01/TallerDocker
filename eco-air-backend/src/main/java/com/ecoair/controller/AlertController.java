package com.ecoair.controller;

import com.ecoair.entity.EnvironmentalAlert;
import com.ecoair.repository.EnvironmentalAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final EnvironmentalAlertRepository alertRepository;

    public AlertController(EnvironmentalAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "eco-air-backend");
        status.put("status", "UP");
        status.put("operatorId", System.getenv().getOrDefault("OPERATOR_ID", "UNKNOWN"));
        status.put("totalAlerts", alertRepository.count());
        status.put("runtimeJavaVersion", System.getProperty("java.version"));
        status.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        status.put("totalMemoryMB", Runtime.getRuntime().totalMemory() / (1024 * 1024));
        status.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));
        return ResponseEntity.ok(status);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<EnvironmentalAlert>> getAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    @PostMapping("/alerts")
    public ResponseEntity<EnvironmentalAlert> createAlert(@RequestBody EnvironmentalAlert alert) {
        EnvironmentalAlert saved = alertRepository.save(alert);
        return ResponseEntity.ok(saved);
    }
}
