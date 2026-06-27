package com.example.goride.booking.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTripServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    private AdminTripService service;

    @BeforeEach
    void setUp() {
        service = new AdminTripService(tripRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsTripsWithFiltersAndOneBasedPagination() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-08T23:59:59Z");
        Trip trip = sampleTrip(passenger(10L));
        when(tripRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trip), org.springframework.data.domain.PageRequest.of(1, 2), 3));

        var response = service.listTrips(TripStatus.COMPLETED, from, to, 2, 2);

        ArgumentCaptor<Specification<Trip>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tripRepository).findAll(specificationCaptor.capture(), pageableCaptor.capture());
        assertThat(specificationCaptor.getValue()).isNotNull();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("requestedAt").isDescending()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(99L);
        assertThat(response.items().get(0).passengerId()).isEqualTo(10L);
        assertThat(response.pagination().page()).isEqualTo(2);
        assertThat(response.pagination().size()).isEqualTo(2);
        assertThat(response.pagination().totalItems()).isEqualTo(3);
        assertThat(response.pagination().totalPages()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        assertThatThrownBy(() -> service.listTrips(null, null, null, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
        assertThatThrownBy(() -> service.listTrips(null, null, null, 1, 101))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(tripRepository);
    }

    @Test
    void rejectsDateRangeWhereFromIsAfterTo() {
        Instant from = Instant.parse("2026-06-09T00:00:00Z");
        Instant to = Instant.parse("2026-06-08T00:00:00Z");

        assertThatThrownBy(() -> service.listTrips(null, from, to, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(tripRepository);
    }

    private Trip sampleTrip(User passenger) {
        Trip trip = Trip.create(
                passenger,
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig()
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        ReflectionTestUtils.setField(trip, "requestedAt", Instant.parse("2026-06-05T10:00:00Z"));
        return trip;
    }

    private PricingConfig pricingConfig() {
        return PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private User passenger(Long id) {
        User user = User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
