package com.example.goride.analytics.repository;

import com.example.goride.analytics.domain.MatchingRun;
import com.example.goride.analytics.domain.MatchingRunOutcome;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchingRunRepository extends JpaRepository<MatchingRun, Long> {
    Optional<MatchingRun> findByTripIdAndOutcome(Long tripId, MatchingRunOutcome outcome);

    boolean existsByTripIdAndOutcome(Long tripId, MatchingRunOutcome outcome);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select run
            from MatchingRun run
            where run.trip.id = :tripId
              and run.outcome = com.example.goride.analytics.domain.MatchingRunOutcome.IN_PROGRESS
            """)
    Optional<MatchingRun> findOpenByTripIdForUpdate(@Param("tripId") Long tripId);
}
