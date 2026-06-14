package com.example.goride.driver.controller;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.dto.BookingLocationResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.driver.dto.DriverRouteGeometryResponse;
import com.example.goride.driver.dto.DriverTripRouteRequest;
import com.example.goride.driver.dto.DriverTripRouteResponse;
import com.example.goride.driver.dto.RouteDestinationType;
import com.example.goride.driver.service.DriverTripRoutingService;
import com.example.goride.driver.service.DriverTripStatusService;
import com.example.goride.matching.service.DriverOfferResponseService;
import com.example.goride.payment.service.CashPaymentConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverTripControllerTests {

    @Test
    void routesAssignedDriverUsingAuthenticatedUserId() {
        DriverTripRoutingService routingService = mock(DriverTripRoutingService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        Authentication authentication = mock(Authentication.class);
        DriverTripRouteRequest request = new DriverTripRouteRequest(
                BigDecimal.valueOf(10.76),
                BigDecimal.valueOf(106.69)
        );
        DriverTripRouteResponse expected = new DriverTripRouteResponse(
                99L,
                TripStatus.ACCEPTED,
                RouteDestinationType.PICKUP,
                new BookingLocationResponse(
                        BigDecimal.valueOf(10.77),
                        BigDecimal.valueOf(106.7),
                        "Ben Thanh Market"
                ),
                2300,
                480,
                new DriverRouteGeometryResponse("LineString", List.of(
                        List.of(BigDecimal.valueOf(106.69), BigDecimal.valueOf(10.76)),
                        List.of(BigDecimal.valueOf(106.7), BigDecimal.valueOf(10.77))
                )),
                List.of()
        );
        when(currentUser.requireUserId(authentication)).thenReturn(20L);
        when(routingService.route(20L, 99L, request)).thenReturn(expected);
        DriverTripController controller = new DriverTripController(
                mock(DriverOfferResponseService.class),
                mock(DriverTripStatusService.class),
                routingService,
                mock(CashPaymentConfirmationService.class),
                currentUser
        );

        var response = controller.routeToTripDestination(authentication, 99L, request);

        assertThat(response.data()).isEqualTo(expected);
        verify(routingService).route(20L, 99L, request);
    }
}
