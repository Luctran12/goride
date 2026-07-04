package com.example.goride.chat.repository;

import com.example.goride.chat.domain.TripMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
