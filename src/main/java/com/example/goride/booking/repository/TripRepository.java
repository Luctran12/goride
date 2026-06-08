package com.example.goride.booking.repository;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id and trip.deletedAt is null")
    Optional<Trip> findActiveByIdForUpdate(@Param("id") Long id);

    List<Trip> findByPassengerIdAndDeletedAtIsNullOrderByRequestedAtDesc(Long passengerId);

    List<Trip> findByDriverIdAndDeletedAtIsNullOrderByRequestedAtDesc(Long driverId);

    Optional<Trip> findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByStartedAtDesc(
            Long driverId,
            TripStatus status
    );

    boolean existsByPassengerIdAndStatusInAndDeletedAtIsNull(Long passengerId, Collection<TripStatus> statuses);

    boolean existsByDriverIdAndStatusInAndDeletedAtIsNull(Long driverId, Collection<TripStatus> statuses);

    @Query(
            value = """
                    select trip
                    from Trip trip
                    where trip.deletedAt is null
                      and (:status is null or trip.status = :status)
                      and (:from is null or trip.requestedAt >= :from)
                      and (:to is null or trip.requestedAt <= :to)
                    """,
            countQuery = """
                    select count(trip)
                    from Trip trip
                    where trip.deletedAt is null
                      and (:status is null or trip.status = :status)
                      and (:from is null or trip.requestedAt >= :from)
                      and (:to is null or trip.requestedAt <= :to)
                    """
    )
    Page<Trip> searchAdminTrips(
            @Param("status") TripStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
            select case when count(trip) > 0 then true else false end
            from Trip trip
            left join trip.driver driver
            where trip.id = :tripId
              and trip.deletedAt is null
              and (trip.passenger.id = :userId or driver.id = :userId)
            """)
    boolean existsAccessibleTripTopicByUserId(@Param("tripId") Long tripId, @Param("userId") Long userId);
}
