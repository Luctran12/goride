package com.example.goride.rating.repository;

import com.example.goride.rating.domain.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByTripId(Long tripId);

    Page<Rating> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);
}
