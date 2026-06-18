package com.example.goride.matching.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SearchingTripMatchingService {
    private static final Logger log = LoggerFactory.getLogger(SearchingTripMatchingService.class);

    private final TripRepository tripRepository;
    private final DriverCandidateStore candidateStore;
    private final MatchingService matchingService;
    private final DriverOfferNotifier driverOfferNotifier;

    public SearchingTripMatchingService(
            TripRepository tripRepository,
            DriverCandidateStore candidateStore,
            MatchingService matchingService,
            DriverOfferNotifier driverOfferNotifier
    ) {
        this.tripRepository = tripRepository;
        this.candidateStore = candidateStore;
        this.matchingService = matchingService;
        this.driverOfferNotifier = driverOfferNotifier;
    }

    @Transactional(readOnly = true)
    public void matchOpenSearchingTrips(Long availableDriverId) {
        tripRepository.findByStatusAndDeletedAtIsNullOrderByRequestedAtAsc(TripStatus.SEARCHING)
                .forEach(trip -> matchTripIfNoActiveOffer(availableDriverId, trip));
    }

    private void matchTripIfNoActiveOffer(Long availableDriverId, Trip trip) {
        if (candidateStore.findTripMatching(trip.getId()).isPresent()) {
            return;
        }

        Optional<DriverOffer> offer = matchingService.findAndLockDriver(MatchingRequest.from(trip));
        offer.ifPresentOrElse(
                nextOffer -> notifyDriver(trip, nextOffer),
                () -> log.info(
                        "Driver availability did not match open trip driverId={} tripId={}",
                        availableDriverId,
                        trip.getId()
                )
        );
    }

    private void notifyDriver(Trip trip, DriverOffer offer) {
        log.info(
                "Driver availability matched open trip tripId={} driverId={} attempt={} expiresAt={}",
                trip.getId(),
                offer.candidate().driverId(),
                offer.attempt(),
                offer.offerExpiresAt()
        );
        driverOfferNotifier.notifyDriver(
                offer.candidate().driverId(),
                DriverOfferNotification.from(trip, offer)
        );
    }
}
