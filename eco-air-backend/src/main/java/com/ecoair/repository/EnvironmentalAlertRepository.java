package com.ecoair.repository;

import com.ecoair.entity.EnvironmentalAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnvironmentalAlertRepository extends JpaRepository<EnvironmentalAlert, Long> {
    List<EnvironmentalAlert> findBySensorId(String sensorId);
    List<EnvironmentalAlert> findBySeverity(String severity);
}
