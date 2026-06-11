package com.example.goride.tracking.repository;

import com.example.goride.tracking.domain.TripLocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripLocationHistoryRepository extends JpaRepository<TripLocationHistory, Long> {
    List<TripLocationHistory> findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(Long tripId);
}
