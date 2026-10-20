package com.ecoair.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "environmental_alerts")
public class EnvironmentalAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sensorId;

    @Column(nullable = false)
    private String metricType; // CO2, PM2_5, TEMPERATURE, HUMIDITY

    @Column(name = "metric_value", nullable = false)
    private Double value;

    @Column(nullable = false)
    private String severity; // LOW, MEDIUM, CRITICAL

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public EnvironmentalAlert() {
        this.timestamp = LocalDateTime.now();
    }

    public EnvironmentalAlert(String sensorId, String metricType, Double value, String severity) {
        this.sensorId = sensorId;
        this.metricType = metricType;
        this.value = value;
        this.severity = severity;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
