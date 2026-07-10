package com.example.goride.servicearea.repository;

import com.example.goride.servicearea.domain.ServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceAreaRepository extends JpaRepository<ServiceArea, Long> {
    List<ServiceArea> findByActiveTrueOrderByCityNameAscNameAsc();

    List<ServiceArea> findAllByOrderByCityNameAscNameAsc();
}