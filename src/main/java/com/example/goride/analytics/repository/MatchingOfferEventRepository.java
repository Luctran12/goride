package com.example.goride.analytics.repository;

import com.example.goride.analytics.domain.MatchingOfferEvent;
import com.example.goride.analytics.domain.MatchingOfferOutcome;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface MatchingOfferEventRepository extends JpaRepository<MatchingOfferEvent, Long> {
    Optional<MatchingOfferEvent> findByMatchingRunIdAndAttemptNo(Long matchingRunId, int attemptNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchingOfferEvent> findFirstByMatchingRunIdAndOutcomeOrderByAttemptNoDesc(
            Long matchingRunId,
            MatchingOfferOutcome outcome
    );
}
