package com.example.goride.booking.repository;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByIdAndDeletedAtIsNull(Long id);

    List<Trip> findByPassengerIdAndDeletedAtIsNullOrderByRequestedAtDesc(Long passengerId);

    List<Trip> findByDriverIdAndDeletedAtIsNullOrderByRequestedAtDesc(Long driverId);

    boolean existsByPassengerIdAndStatusInAndDeletedAtIsNull(Long passengerId, Collection<TripStatus> statuses);

    boolean existsByDriverIdAndStatusInAndDeletedAtIsNull(Long driverId, Collection<TripStatus> statuses);
}
