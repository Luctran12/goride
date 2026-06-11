package com.example.goride.rating.repository;

import com.example.goride.rating.domain.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByTripId(Long tripId);

    Optional<Rating> findByTripId(Long tripId);

    Page<Rating> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);
}
