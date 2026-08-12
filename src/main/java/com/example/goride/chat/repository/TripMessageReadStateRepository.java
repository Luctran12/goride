package com.example.goride.chat.repository;

import com.example.goride.chat.domain.TripMessageReadState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripMessageReadStateRepository extends JpaRepository<TripMessageReadState, Long> {
    Optional<TripMessageReadState> findByTripIdAndUserId(Long tripId, Long userId);
}
