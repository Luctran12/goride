package com.example.goride.booking.repository;

import com.example.goride.booking.domain.TripStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistory, Long> {
    List<TripStatusHistory> findByTripIdOrderByChangedAtAsc(Long tripId);
}
