package com.example.goride.servicearea.service;

import com.example.goride.booking.service.distance.Location;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.servicearea.domain.ServiceArea;
import com.example.goride.servicearea.dto.ServiceAreaCoordinateRequest;
import com.example.goride.servicearea.dto.ServiceAreaCreateRequest;
import com.example.goride.servicearea.dto.ServiceAreaResponse;
import com.example.goride.servicearea.dto.ServiceAreaUpdateRequest;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ServiceAreaService {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ServiceAreaRepository serviceAreaRepository;

    public ServiceAreaService(ServiceAreaRepository serviceAreaRepository) {
        this.serviceAreaRepository = serviceAreaRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceAreaResponse> listActive() {
        return serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc()
                .stream()
                .map(ServiceAreaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceAreaResponse> listAll() {
        return serviceAreaRepository.findAllByOrderByCityNameAscNameAsc()
                .stream()
                .map(ServiceAreaResponse::from)
                .toList();
    }

    @Transactional
    public ServiceAreaResponse create(ServiceAreaCreateRequest request) {
        try {
            ServiceArea area = ServiceArea.create(
                    request.name(),
                    request.cityName(),
                    request.countryCode(),
                    polygonFrom(request.boundary()),
                    request.active()
            );
            return ServiceAreaResponse.from(serviceAreaRepository.save(area));
        } catch (IllegalArgumentException exception) {
            throw validationError(exception);
        }
    }

    @Transactional
    public ServiceAreaResponse update(Long serviceAreaId, ServiceAreaUpdateRequest request) {
        ServiceArea area = getServiceArea(serviceAreaId);
        try {
            area.update(
                    request.name(),
                    request.cityName(),
                    request.countryCode(),
                    request.boundary() == null ? null : polygonFrom(request.boundary()),
                    request.active()
            );
            return ServiceAreaResponse.from(serviceAreaRepository.save(area));
        } catch (IllegalArgumentException exception) {
            throw validationError(exception);
        }
    }

    @Transactional
    public ServiceAreaResponse deactivate(Long serviceAreaId) {
        ServiceArea area = getServiceArea(serviceAreaId);
        area.deactivate();
        return ServiceAreaResponse.from(serviceAreaRepository.save(area));
    }

    @Transactional(readOnly = true)
    public Optional<ServiceAreaResponse> validateTripWithinServiceArea(Location pickup, Location dropoff) {
        List<ServiceArea> activeAreas = serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc();
        if (activeAreas.isEmpty()) {
            return Optional.empty();
        }

        Point pickupPoint = pointFrom(pickup);
        Point dropoffPoint = pointFrom(dropoff);
        List<ServiceArea> pickupAreas = activeAreas.stream()
                .filter(area -> area.covers(pickupPoint))
                .toList();
        if (pickupAreas.isEmpty()) {
            throw outOfServiceArea("pickup", pickup);
        }

        List<ServiceArea> dropoffAreas = activeAreas.stream()
                .filter(area -> area.covers(dropoffPoint))
                .toList();
        if (dropoffAreas.isEmpty()) {
            throw outOfServiceArea("dropoff", dropoff);
        }

        ServiceArea commonArea = pickupAreas.stream()
                .filter(pickupArea -> dropoffAreas.stream()
                        .anyMatch(dropoffArea -> isSameArea(pickupArea, dropoffArea)))
                .findFirst()
                .orElseThrow(() -> differentServiceAreas(pickupAreas.get(0), dropoffAreas.get(0)));
        return Optional.of(ServiceAreaResponse.from(commonArea));
    }

    private boolean isSameArea(ServiceArea pickupArea, ServiceArea dropoffArea) {
        if (pickupArea == dropoffArea) {
            return true;
        }
        return pickupArea.getId() != null && pickupArea.getId().equals(dropoffArea.getId());
    }

    private BusinessException differentServiceAreas(ServiceArea pickupArea, ServiceArea dropoffArea) {
        return new BusinessException(
                ErrorCode.LOCATION_OUT_OF_SERVICE_AREA,
                "Pickup and dropoff must be inside the same service area",
                Map.of(
                        "pickupServiceAreaId", pickupArea.getId(),
                        "pickupServiceAreaName", pickupArea.getName(),
                        "dropoffServiceAreaId", dropoffArea.getId(),
                        "dropoffServiceAreaName", dropoffArea.getName()
                )
        );
    }

    private BusinessException validationError(IllegalArgumentException exception) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage());
    }

    private ServiceArea getServiceArea(Long serviceAreaId) {
        return serviceAreaRepository.findById(serviceAreaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_AREA_NOT_FOUND));
    }

    private BusinessException outOfServiceArea(String locationType, Location location) {
        return new BusinessException(
                ErrorCode.LOCATION_OUT_OF_SERVICE_AREA,
                "Location is outside active service areas",
                Map.of(
                        "location", locationType,
                        "latitude", location.latitude(),
                        "longitude", location.longitude()
                )
        );
    }

    private Point pointFrom(Location location) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(
                location.longitude().doubleValue(),
                location.latitude().doubleValue()
        ));
    }

    private Polygon polygonFrom(List<ServiceAreaCoordinateRequest> boundary) {
        if (boundary == null || boundary.size() < 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Boundary must have at least 3 points");
        }
        validateDistinctPoints(boundary);

        List<ServiceAreaCoordinateRequest> normalizedBoundary = removeClosingPoint(boundary);
        Coordinate[] coordinates = new Coordinate[normalizedBoundary.size() + 1];
        for (int index = 0; index < normalizedBoundary.size(); index++) {
            ServiceAreaCoordinateRequest point = normalizedBoundary.get(index);
            coordinates[index] = coordinateFrom(point);
        }
        coordinates[coordinates.length - 1] = new Coordinate(coordinates[0]);

        LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(ring);
        polygon.setSRID(4326);
        if (!polygon.isValid()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Boundary polygon is invalid");
        }
        return polygon;
    }

    private List<ServiceAreaCoordinateRequest> removeClosingPoint(List<ServiceAreaCoordinateRequest> boundary) {
        ServiceAreaCoordinateRequest first = boundary.get(0);
        ServiceAreaCoordinateRequest last = boundary.get(boundary.size() - 1);
        if (sameCoordinate(first, last)) {
            return boundary.subList(0, boundary.size() - 1);
        }
        return boundary;
    }

    private boolean sameCoordinate(ServiceAreaCoordinateRequest first, ServiceAreaCoordinateRequest second) {
        return first.lat().compareTo(second.lat()) == 0 && first.lng().compareTo(second.lng()) == 0;
    }

    private void validateDistinctPoints(List<ServiceAreaCoordinateRequest> boundary) {
        Set<String> points = new HashSet<>();
        for (ServiceAreaCoordinateRequest point : boundary) {
            points.add(point.lat().stripTrailingZeros().toPlainString()
                    + ":"
                    + point.lng().stripTrailingZeros().toPlainString());
        }
        if (points.size() < 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Boundary must have at least 3 distinct points");
        }
    }

    private Coordinate coordinateFrom(ServiceAreaCoordinateRequest point) {
        BigDecimal longitude = point.lng();
        BigDecimal latitude = point.lat();
        return new Coordinate(longitude.doubleValue(), latitude.doubleValue());
    }
}