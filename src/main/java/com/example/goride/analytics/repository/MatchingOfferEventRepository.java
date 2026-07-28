package com.example.goride.analytics.repository;

import com.example.goride.analytics.domain.MatchingOfferEvent;
import com.example.goride.analytics.domain.MatchingOfferOutcome;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchingOfferEventRepository extends JpaRepository<MatchingOfferEvent, Long> {
    Optional<MatchingOfferEvent> findByMatchingRunIdAndAttemptNo(Long matchingRunId, int attemptNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MatchingOfferEvent> findFirstByMatchingRunIdAndOutcomeOrderByAttemptNoDesc(
            Long matchingRunId,
            MatchingOfferOutcome outcome
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select offer
            from MatchingOfferEvent offer
            where offer.matchingRun.id = :matchingRunId
              and offer.attemptNo = :attemptNo
            """)
    Optional<MatchingOfferEvent> findByRunAndAttemptForUpdate(
            @Param("matchingRunId") Long matchingRunId,
            @Param("attemptNo") int attemptNo
    );

    @Query("""
            select max(offer.attemptNo)
            from MatchingOfferEvent offer
            where offer.matchingRun.id = :matchingRunId
            """)
    Optional<Integer> findMaxAttemptNo(@Param("matchingRunId") Long matchingRunId);

    @Query("""
            select offer
            from MatchingOfferEvent offer
            join fetch offer.matchingRun run
            join fetch run.trip trip
            where offer.outcome = com.example.goride.analytics.domain.MatchingOfferOutcome.OFFERED
              and run.outcome = com.example.goride.analytics.domain.MatchingRunOutcome.IN_PROGRESS
              and offer.expiresAt <= :cutoff
            order by offer.expiresAt, offer.id
            """)
    List<MatchingOfferEvent> findExpiredOpenOffers(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Query("""
            select offer.driver.id
            from MatchingOfferEvent offer
            where offer.matchingRun.id = :matchingRunId
              and offer.outcome in :outcomes
            """)
    List<Long> findDriverIdsByRunAndOutcomeIn(
            @Param("matchingRunId") Long matchingRunId,
            @Param("outcomes") Collection<MatchingOfferOutcome> outcomes
    );
}
