package com.example.goride.payment.repository;

import com.example.goride.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTripId(Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from Payment payment
            join fetch payment.trip trip
            join fetch trip.passenger passenger
            left join fetch trip.driver driver
            where trip.id = :tripId
              and trip.deletedAt is null
            """)
    Optional<Payment> findByTripIdForUpdate(@Param("tripId") Long tripId);
}
