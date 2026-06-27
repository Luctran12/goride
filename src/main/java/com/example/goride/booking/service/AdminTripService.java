package com.example.goride.booking.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.dto.TripResponse;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminTripService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TripRepository tripRepository;

    public AdminTripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> listTrips(
            TripStatus status,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        validateSearchRequest(from, to, page, size);
        PageRequest pageRequest = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "requestedAt")
        );
        Page<Trip> trips = tripRepository.findAll(adminTripSearch(status, from, to), pageRequest);
        return PageResponse.of(
                trips.getContent().stream()
                        .map(TripResponse::from)
                        .toList(),
                page,
                size,
                trips.getTotalElements()
        );
    }


    private Specification<Trip> adminTripSearch(TripStatus status, Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.isNull(root.get("deletedAt"));
            if (status != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.greaterThanOrEqualTo(root.get("requestedAt"), from)
                );
            }
            if (to != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.lessThanOrEqualTo(root.get("requestedAt"), to)
                );
            }
            return predicate;
        };
    }

    private void validateSearchRequest(Instant from, Instant to, int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "From must be before or equal to to");
        }
    }
}
