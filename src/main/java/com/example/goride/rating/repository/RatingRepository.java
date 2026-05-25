package com.example.goride.rating.repository;

import com.example.goride.rating.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByTripId(Long tripId);
}
