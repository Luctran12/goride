package com.example.goride.booking.repository;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.driver.domain.VehicleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip> {
    Optional<Trip> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id and trip.deletedAt is null")
    Optional<Trip> findActiveByIdForUpdate(@Param("id") Long id);

    List<Trip> findByPassengerIdAndDeletedAtIsNullOrderByRequestedAtDesc(Long passengerId);

    List<Trip> findByDriverIdAndDeletedAtIsNullOrderByRequestedAtDesc(Long driverId);

    @EntityGraph(attributePaths = "passenger")
    List<Trip> findByStatusAndDeletedAtIsNullOrderByRequestedAtAsc(TripStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select trip
            from Trip trip
            where trip.status = :status
              and trip.scheduledPickupTime <= :dispatchBefore
              and trip.deletedAt is null
            order by trip.scheduledPickupTime asc, trip.requestedAt asc
            """)
    List<Trip> findReadyScheduledTripsForUpdate(
            @Param("status") TripStatus status,
            @Param("dispatchBefore") Instant dispatchBefore,
            Pageable pageable
    );

    Optional<Trip> findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByStartedAtDesc(
            Long driverId,
            TripStatus status
    );

    Optional<Trip> findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByAcceptedAtDesc(
            Long driverId,
            Collection<TripStatus> statuses
    );

    boolean existsByPassengerIdAndStatusInAndDeletedAtIsNull(Long passengerId, Collection<TripStatus> statuses);

    boolean existsByDriverIdAndStatusInAndDeletedAtIsNull(Long driverId, Collection<TripStatus> statuses);

    long countByDeletedAtIsNull();

    long countByVehicleTypeAndStatusAndDeletedAtIsNull(VehicleType vehicleType, TripStatus status);

    @Query("""
            select trip.status as status, count(trip) as total
            from Trip trip
            where trip.deletedAt is null
            group by trip.status
            """)
    List<TripStatusCount> countTripsByStatus();


    @Query("""
            select case when count(trip) > 0 then true else false end
            from Trip trip
            left join trip.driver driver
            where trip.id = :tripId
              and trip.deletedAt is null
              and (trip.passenger.id = :userId or driver.id = :userId)
            """)
    boolean existsAccessibleTripTopicByUserId(@Param("tripId") Long tripId, @Param("userId") Long userId);

    interface TripStatusCount {
        TripStatus getStatus();

        long getTotal();
    }
}
