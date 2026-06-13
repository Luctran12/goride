package com.example.goride.payment.repository;

import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTripId(Long tripId);

    @Query("""
            select payment
            from Payment payment
            join fetch payment.trip trip
            join fetch trip.passenger passenger
            left join fetch trip.driver driver
            where trip.id = :tripId
              and trip.deletedAt is null
            """)
    Optional<Payment> findByTripIdWithTrip(@Param("tripId") Long tripId);

    long countByStatus(PaymentStatus status);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from Payment payment
            where payment.status = :status
            """)
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
            from Payment payment
            join fetch payment.trip trip
            join fetch trip.passenger passenger
            left join fetch trip.driver driver
            where payment.id = :paymentId
              and trip.deletedAt is null
            """)
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
}
