package com.example.goride.booking.config;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.driver.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingConfigSeederTests {
    @Mock
    private PricingConfigRepository pricingConfigRepository;

    @Test
    void seedsDefaultPricingWhenMissing() {
        PricingConfigSeeder seeder = new PricingConfigSeeder(pricingConfigRepository);

        seeder.run(null);

        ArgumentCaptor<PricingConfig> captor = ArgumentCaptor.forClass(PricingConfig.class);
        verify(pricingConfigRepository).existsByVehicleTypeAndActiveTrue(VehicleType.MOTORBIKE);
        verify(pricingConfigRepository).existsByVehicleTypeAndActiveTrue(VehicleType.CAR_4_SEAT);
        verify(pricingConfigRepository).existsByVehicleTypeAndActiveTrue(VehicleType.CAR_7_SEAT);
        verify(pricingConfigRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PricingConfig::getVehicleType)
                .containsExactly(VehicleType.MOTORBIKE, VehicleType.CAR_4_SEAT, VehicleType.CAR_7_SEAT);
    }

    @Test
    void keepsExistingActivePricing() {
        when(pricingConfigRepository.existsByVehicleTypeAndActiveTrue(VehicleType.MOTORBIKE)).thenReturn(true);
        when(pricingConfigRepository.existsByVehicleTypeAndActiveTrue(VehicleType.CAR_4_SEAT)).thenReturn(true);
        when(pricingConfigRepository.existsByVehicleTypeAndActiveTrue(VehicleType.CAR_7_SEAT)).thenReturn(true);
        PricingConfigSeeder seeder = new PricingConfigSeeder(pricingConfigRepository);

        seeder.run(null);

        verify(pricingConfigRepository, never()).save(org.mockito.Mockito.any(PricingConfig.class));
    }
}
