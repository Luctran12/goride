package com.example.goride.booking.service;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.dto.PricingConfigCreateRequest;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingConfigServiceTests {
    @Mock
    private PricingConfigRepository pricingConfigRepository;

    private PricingConfigService service;

    @BeforeEach
    void setUp() {
        service = new PricingConfigService(pricingConfigRepository);
    }

    @Test
    void listsActivePricing() {
        PricingConfig pricingConfig = pricingConfig(1L, VehicleType.MOTORBIKE, true);
        when(pricingConfigRepository.findByActiveTrueOrderByVehicleTypeAscEffectiveFromDesc())
                .thenReturn(List.of(pricingConfig));

        var response = service.listActivePricing();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(1L);
        assertThat(response.get(0).vehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(response.get(0).active()).isTrue();
        assertThat(response.get(0).currency()).isEqualTo("VND");
    }

    @Test
    void listsAllPricingForAdmin() {
        PricingConfig active = pricingConfig(1L, VehicleType.MOTORBIKE, true);
        PricingConfig inactive = pricingConfig(2L, VehicleType.MOTORBIKE, false);
        when(pricingConfigRepository.findAllByOrderByVehicleTypeAscEffectiveFromDesc())
                .thenReturn(List.of(active, inactive));

        var response = service.listAllPricing();

        assertThat(response).extracting("id").containsExactly(1L, 2L);
        assertThat(response).extracting("active").containsExactly(true, false);
    }

    @Test
    void createsNewPricingVersionAndDeactivatesCurrentActivePricing() {
        PricingConfig currentActive = pricingConfig(1L, VehicleType.CAR_4_SEAT, true);
        when(pricingConfigRepository.findByVehicleTypeAndActiveTrue(VehicleType.CAR_4_SEAT))
                .thenReturn(List.of(currentActive));
        when(pricingConfigRepository.save(org.mockito.ArgumentMatchers.any(PricingConfig.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 2L));

        var response = service.createPricing(createRequest());

        ArgumentCaptor<PricingConfig> pricingCaptor = ArgumentCaptor.forClass(PricingConfig.class);
        verify(pricingConfigRepository).save(pricingCaptor.capture());
        assertThat(currentActive.isActive()).isFalse();
        assertThat(pricingCaptor.getValue().getVehicleType()).isEqualTo(VehicleType.CAR_4_SEAT);
        assertThat(pricingCaptor.getValue().getBaseFare()).isEqualByComparingTo(BigDecimal.valueOf(18000));
        assertThat(pricingCaptor.getValue().getPerKmRate()).isEqualByComparingTo(BigDecimal.valueOf(8500));
        assertThat(pricingCaptor.getValue().isActive()).isTrue();
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.vehicleType()).isEqualTo(VehicleType.CAR_4_SEAT);
        assertThat(response.active()).isTrue();
    }

    @Test
    void deactivatesPricingById() {
        PricingConfig pricingConfig = pricingConfig(1L, VehicleType.MOTORBIKE, true);
        when(pricingConfigRepository.findById(1L)).thenReturn(Optional.of(pricingConfig));
        when(pricingConfigRepository.save(pricingConfig)).thenReturn(pricingConfig);

        var response = service.deactivatePricing(1L);

        verify(pricingConfigRepository).save(pricingConfig);
        assertThat(pricingConfig.isActive()).isFalse();
        assertThat(response.active()).isFalse();
    }

    @Test
    void rejectsFutureEffectiveTimeWhenCreatingPricing() {
        PricingConfigCreateRequest request = new PricingConfigCreateRequest(
                VehicleType.CAR_4_SEAT,
                BigDecimal.valueOf(18000),
                BigDecimal.valueOf(8500),
                BigDecimal.valueOf(600),
                BigDecimal.valueOf(28000),
                BigDecimal.valueOf(1.1),
                Instant.now().plusSeconds(60)
        );

        assertThatThrownBy(() -> service.createPricing(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(pricingConfigRepository);
    }

    @Test
    void rejectsMissingPricingWhenDeactivating() {
        when(pricingConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivatePricing(99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PRICING_CONFIG_NOT_FOUND)
                );
    }

    private PricingConfigCreateRequest createRequest() {
        return new PricingConfigCreateRequest(
                VehicleType.CAR_4_SEAT,
                BigDecimal.valueOf(18000),
                BigDecimal.valueOf(8500),
                BigDecimal.valueOf(600),
                BigDecimal.valueOf(28000),
                BigDecimal.valueOf(1.1),
                Instant.parse("2026-06-01T00:00:00Z")
        );
    }

    private PricingConfig pricingConfig(Long id, VehicleType vehicleType, boolean active) {
        PricingConfig pricingConfig = PricingConfig.create(
                vehicleType,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        withId(pricingConfig, id);
        if (!active) {
            pricingConfig.deactivate();
        }
        return pricingConfig;
    }

    private PricingConfig withId(PricingConfig pricingConfig, Long id) {
        ReflectionTestUtils.setField(pricingConfig, "id", id);
        return pricingConfig;
    }
}
