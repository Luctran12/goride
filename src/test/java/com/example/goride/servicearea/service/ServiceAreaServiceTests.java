package com.example.goride.servicearea.service;

import com.example.goride.booking.service.distance.Location;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.servicearea.domain.ServiceArea;
import com.example.goride.servicearea.dto.ServiceAreaCoordinateRequest;
import com.example.goride.servicearea.dto.ServiceAreaCreateRequest;
import com.example.goride.servicearea.dto.ServiceAreaUpdateRequest;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAreaServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private ServiceAreaRepository serviceAreaRepository;

    @InjectMocks
    private ServiceAreaService serviceAreaService;

    @Test
    void createStoresClosedPolygonAndMapsBoundaryWithoutClosingPoint() {
        when(serviceAreaRepository.save(org.mockito.ArgumentMatchers.any(ServiceArea.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        var response = serviceAreaService.create(new ServiceAreaCreateRequest(
                "Ho Chi Minh Core",
                "Ho Chi Minh",
                null,
                true,
                hcmCoreBoundary()
        ));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Ho Chi Minh Core");
        assertThat(response.cityName()).isEqualTo("Ho Chi Minh");
        assertThat(response.countryCode()).isEqualTo("VN");
        assertThat(response.active()).isTrue();
        assertThat(response.boundary()).hasSize(4);
        assertThat(response.boundary().get(0).lat()).isEqualByComparingTo(BigDecimal.valueOf(10.70));
        assertThat(response.boundary().get(0).lng()).isEqualByComparingTo(BigDecimal.valueOf(106.60));
    }

    @Test
    void createAcceptsAlreadyClosedBoundaryWithoutDuplicatingClosingPoint() {
        when(serviceAreaRepository.save(org.mockito.ArgumentMatchers.any(ServiceArea.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        var response = serviceAreaService.create(new ServiceAreaCreateRequest(
                "Ho Chi Minh Core",
                "Ho Chi Minh",
                "VN",
                true,
                hcmCoreBoundaryWithClosingPoint()
        ));

        assertThat(response.boundary()).hasSize(4);
        assertThat(response.boundary().get(response.boundary().size() - 1)).isNotEqualTo(response.boundary().get(0));
    }

    @Test
    void createRejectsInvalidCountryCodeAsValidationError() {
        assertThatThrownBy(() -> serviceAreaService.create(new ServiceAreaCreateRequest(
                "Ho Chi Minh Core",
                "Ho Chi Minh",
                "V1",
                true,
                hcmCoreBoundary()
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
    }

    @Test
    void validateTripAllowsAnyLocationsWhenNoActiveServiceAreasExist() {
        when(serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc()).thenReturn(List.of());

        Optional<?> response = serviceAreaService.validateTripWithinServiceArea(
                location(10.7769, 106.7009),
                location(10.7850, 106.6800)
        );

        assertThat(response).isEmpty();
    }

    @Test
    void validateTripAcceptsPickupAndDropoffInsideSameActiveServiceArea() {
        ServiceArea area = withId(activeArea("Ho Chi Minh Core", square(10.70, 106.60, 10.90, 106.90)), 10L);
        when(serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc()).thenReturn(List.of(area));

        var response = serviceAreaService.validateTripWithinServiceArea(
                location(10.7769, 106.7009),
                location(10.7850, 106.6800)
        );

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().id()).isEqualTo(10L);
        assertThat(response.orElseThrow().name()).isEqualTo("Ho Chi Minh Core");
    }

    @Test
    void validateTripAcceptsLocationsWhenTheyShareAnOverlappingServiceArea() {
        ServiceArea hcm = withId(activeArea("Ho Chi Minh Core", square(10.70, 106.60, 10.90, 106.90)), 10L);
        ServiceArea district = withId(activeArea("District Core", square(10.75, 106.65, 10.82, 106.75)), 11L);
        when(serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc()).thenReturn(List.of(district, hcm));

        var response = serviceAreaService.validateTripWithinServiceArea(
                location(10.7769, 106.7009),
                location(10.8600, 106.8600)
        );

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().id()).isEqualTo(10L);
    }

    @Test
    void validateTripRejectsPickupOutsideActiveServiceAreas() {
        ServiceArea area = withId(activeArea("Ho Chi Minh Core", square(10.70, 106.60, 10.90, 106.90)), 10L);
        when(serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc()).thenReturn(List.of(area));

        assertThatThrownBy(() -> serviceAreaService.validateTripWithinServiceArea(
                location(11.0000, 106.7009),
                location(10.7850, 106.6800)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.LOCATION_OUT_OF_SERVICE_AREA);
                    assertThat(exception.details()).containsEntry("location", "pickup");
                });
    }

    @Test
    void validateTripRejectsDropoffInDifferentServiceArea() {
        ServiceArea hcm = withId(activeArea("Ho Chi Minh Core", square(10.70, 106.60, 10.90, 106.90)), 10L);
        ServiceArea thuDuc = withId(activeArea("Thu Duc", square(10.95, 106.75, 11.05, 106.90)), 11L);
        when(serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc()).thenReturn(List.of(hcm, thuDuc));

        assertThatThrownBy(() -> serviceAreaService.validateTripWithinServiceArea(
                location(10.7769, 106.7009),
                location(11.0000, 106.8000)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.LOCATION_OUT_OF_SERVICE_AREA);
                    assertThat(exception.details()).containsEntry("pickupServiceAreaId", 10L);
                    assertThat(exception.details()).containsEntry("dropoffServiceAreaId", 11L);
                });
    }

    @Test
    void updateChangesOptionalFieldsAndBoundary() {
        ServiceArea area = withId(activeArea("Ho Chi Minh Core", square(10.70, 106.60, 10.90, 106.90)), 10L);
        when(serviceAreaRepository.findById(10L)).thenReturn(Optional.of(area));
        when(serviceAreaRepository.save(area)).thenReturn(area);

        var response = serviceAreaService.update(10L, new ServiceAreaUpdateRequest(
                "Saigon Core",
                null,
                "vn",
                false,
                List.of(
                        coordinate(10.71, 106.61),
                        coordinate(10.71, 106.80),
                        coordinate(10.88, 106.80),
                        coordinate(10.88, 106.61)
                )
        ));

        assertThat(response.name()).isEqualTo("Saigon Core");
        assertThat(response.countryCode()).isEqualTo("VN");
        assertThat(response.active()).isFalse();
        assertThat(response.boundary()).hasSize(4);
    }

    @Test
    void deactivateMarksAreaInactive() {
        ServiceArea area = withId(activeArea("Ho Chi Minh Core", square(10.70, 106.60, 10.90, 106.90)), 10L);
        when(serviceAreaRepository.findById(10L)).thenReturn(Optional.of(area));
        when(serviceAreaRepository.save(area)).thenReturn(area);

        var response = serviceAreaService.deactivate(10L);

        assertThat(response.active()).isFalse();
        verify(serviceAreaRepository).save(area);
    }

    @Test
    void updateRejectsMissingServiceArea() {
        when(serviceAreaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAreaService.update(999L, new ServiceAreaUpdateRequest(
                "Missing",
                null,
                null,
                null,
                null
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.SERVICE_AREA_NOT_FOUND)
                );
    }

    @Test
    void createRejectsBoundaryWithLessThanThreeDistinctPoints() {
        assertThatThrownBy(() -> serviceAreaService.create(new ServiceAreaCreateRequest(
                "Invalid",
                "Ho Chi Minh",
                "VN",
                true,
                List.of(
                        coordinate(10.70, 106.60),
                        coordinate(10.70, 106.60),
                        coordinate(10.80, 106.80)
                )
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
    }

    private List<ServiceAreaCoordinateRequest> hcmCoreBoundary() {
        return List.of(
                coordinate(10.70, 106.60),
                coordinate(10.70, 106.90),
                coordinate(10.90, 106.90),
                coordinate(10.90, 106.60)
        );
    }

    private List<ServiceAreaCoordinateRequest> hcmCoreBoundaryWithClosingPoint() {
        return List.of(
                coordinate(10.70, 106.60),
                coordinate(10.70, 106.90),
                coordinate(10.90, 106.90),
                coordinate(10.90, 106.60),
                coordinate(10.70, 106.60)
        );
    }

    private ServiceArea activeArea(String name, Polygon boundary) {
        return ServiceArea.create(name, "Ho Chi Minh", "VN", boundary, true);
    }

    private Polygon square(double minLat, double minLng, double maxLat, double maxLng) {
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
                new Coordinate(minLng, minLat),
                new Coordinate(maxLng, minLat),
                new Coordinate(maxLng, maxLat),
                new Coordinate(minLng, maxLat),
                new Coordinate(minLng, minLat)
        });
        polygon.setSRID(4326);
        return polygon;
    }

    private ServiceArea withId(ServiceArea area, Long id) {
        ReflectionTestUtils.setField(area, "id", id);
        return area;
    }

    private ServiceAreaCoordinateRequest coordinate(double lat, double lng) {
        return new ServiceAreaCoordinateRequest(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
    }

    private Location location(double lat, double lng) {
        return new Location(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
    }
}