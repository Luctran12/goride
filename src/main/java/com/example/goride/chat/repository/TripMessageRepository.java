package com.example.goride.chat.repository;

import com.example.goride.chat.domain.TripMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripMessageRepository extends JpaRepository<TripMessage, Long> {
    @Query(value = """
            select message
            from TripMessage message
            join fetch message.sender
            where message.trip.id = :tripId
              and message.trip.deletedAt is null
            """,
            countQuery = """
                    select count(message)
                    from TripMessage message
                    where message.trip.id = :tripId
                      and message.trip.deletedAt is null
                    """)
    Page<TripMessage> findLatestByTripId(@Param("tripId") Long tripId, Pageable pageable);

    Optional<TripMessage> findByTripIdAndSenderIdAndClientMessageId(
            Long tripId,
            Long senderId,
            UUID clientMessageId
    );

    Optional<TripMessage> findByIdAndTripId(Long id, Long tripId);

    List<TripMessage> findByTripIdOrderByIdDesc(Long tripId, Pageable pageable);

    List<TripMessage> findByTripIdAndIdLessThanOrderByIdDesc(Long tripId, Long beforeId, Pageable pageable);

    List<TripMessage> findByTripIdAndIdGreaterThanOrderByIdAsc(Long tripId, Long afterId, Pageable pageable);

    long countByTripIdAndSenderIdNot(Long tripId, Long senderId);

    long countByTripIdAndIdGreaterThanAndSenderIdNot(Long tripId, Long messageId, Long senderId);
}
