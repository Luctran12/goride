package com.example.goride.analytics.repository;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DirectAnalyticsQueryPort {
    OverviewStats overview(AnalyticsFilter filter);

    List<DemandBucketStats> demandTimeseries(AnalyticsFilter filter, AnalyticsBucket bucket);

    List<SupplyBucketStats> supplyTimeseries(AnalyticsFilter filter, AnalyticsBucket bucket);

    MatchingPerformanceStats matchingPerformance(AnalyticsFilter filter);

    FunnelStats matchingFunnel(AnalyticsFilter filter);

    record OverviewStats(
            long tripRequests,
            long completedTrips,
            long completedTripsByRequestCohort,
            long cancelledTrips,
            long noDriverTrips,
            long completedPayments,
            BigDecimal completedRevenue,
            long matchingRuns,
            long terminalRuns,
            long matchedRuns,
            BigDecimal averageMatchingDurationMs,
            BigDecimal p50MatchingDurationMs,
            BigDecimal p95MatchingDurationMs
    ) {
    }

    record DemandBucketStats(
            LocalDateTime bucketStart,
            long tripRequests,
            long completedTripsByRequestCohort
    ) {
    }

    record SupplyBucketStats(
            LocalDateTime bucketStart,
            BigDecimal averageOnlineDrivers,
            BigDecimal averageAvailableDrivers,
            BigDecimal averageBusyDrivers,
            long observedBuckets
    ) {
    }

    record MatchingPerformanceStats(
            long matchingRuns,
            long terminalRuns,
            long matchedRuns,
            long noDriverRuns,
            long cancelledRuns,
            long failedRuns,
            BigDecimal averageMatchingDurationMs,
            BigDecimal p50MatchingDurationMs,
            BigDecimal p95MatchingDurationMs,
            BigDecimal averageSearchesPerRun,
            BigDecimal averageCandidatesPerRun,
            BigDecimal averageOffersPerRun,
            long terminalOffers,
            long acceptedOffers,
            long rejectedOffers,
            long timedOutOffers,
            BigDecimal averageCandidateDistanceM
    ) {
    }

    record FunnelStats(
            long runStarted,
            long candidateFound,
            long offerSent,
            long offerAccepted,
            long tripCompleted
    ) {
    }
}
