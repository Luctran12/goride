package com.example.goride.booking.service;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.SurgePricingRule;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.dto.SurgePricingRuleCreateRequest;
import com.example.goride.booking.dto.SurgePricingRuleUpdateRequest;
import com.example.goride.booking.repository.SurgePricingRuleRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.repository.DriverProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgePricingServiceTests {
    private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");

    @Mock
    private SurgePricingRuleRepository surgePricingRuleRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    private SurgePricingService service;

    @BeforeEach
    void setUp() {
        service = new SurgePricingService(
                surgePricingRuleRepository,
                tripRepository,
                driverProfileRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void quoteAppliesHighestMatchingDynamicRule() {
        SurgePricingRule lowerRule = rule(1L, "Busy", BigDecimal.valueOf(1.50), BigDecimal.valueOf(1.10));
        SurgePricingRule peakRule = rule(2L, "Peak", BigDecimal.valueOf(2.00), BigDecimal.valueOf(1.25));
        when(tripRepository.countByVehicleTypeAndStatusAndDeletedAtIsNull(
                VehicleType.MOTORBIKE,
                TripStatus.SEARCHING
        )).thenReturn(2L);
        when(driverProfileRepository.countOnlineApprovedByVehicleType(VehicleType.MOTORBIKE)).thenReturn(1L);
        when(surgePricingRuleRepository.findByVehicleTypeAndActiveTrueOrderByMinDemandSupplyRatioDescMultiplierDesc(
                VehicleType.MOTORBIKE
        )).thenReturn(List.of(peakRule, lowerRule));

        var quote = service.quote(pricingConfig(BigDecimal.valueOf(1.10)), true);

        assertThat(quote.demandTrips()).isEqualTo(3);
        assertThat(quote.onlineDrivers()).isEqualTo(1);
        assertThat(quote.demandSupplyRatio()).isEqualByComparingTo(BigDecimal.valueOf(3.00));
        assertThat(quote.dynamicSurgeMultiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.25));
        assertThat(quote.effectiveSurgeMultiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.38));
        assertThat(quote.ruleId()).isEqualTo(2L);
        assertThat(quote.ruleName()).isEqualTo("Peak");
        assertThat(quote.surgeApplied()).isTrue();
    }

    @Test
    void quoteFallsBackToPricingMultiplierWhenNoRuleMatches() {
        when(tripRepository.countByVehicleTypeAndStatusAndDeletedAtIsNull(
                VehicleType.MOTORBIKE,
                TripStatus.SEARCHING
        )).thenReturn(0L);
        when(driverProfileRepository.countOnlineApprovedByVehicleType(VehicleType.MOTORBIKE)).thenReturn(0L);
        when(surgePricingRuleRepository.findByVehicleTypeAndActiveTrueOrderByMinDemandSupplyRatioDescMultiplierDesc(
                VehicleType.MOTORBIKE
        )).thenReturn(List.of(rule(1L, "Busy", BigDecimal.valueOf(2.00), BigDecimal.valueOf(1.20))));

        var quote = service.quote(pricingConfig(BigDecimal.valueOf(1.10)), false);

        assertThat(quote.demandTrips()).isZero();
        assertThat(quote.onlineDrivers()).isZero();
        assertThat(quote.demandSupplyRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.dynamicSurgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(quote.effectiveSurgeMultiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.10));
        assertThat(quote.ruleId()).isNull();
    }

    @Test
    void createsUpdatesAndDeactivatesRules() {
        SurgePricingRule createdRule = rule(10L, "Peak", BigDecimal.valueOf(2.00), BigDecimal.valueOf(1.30));
        when(surgePricingRuleRepository.save(org.mockito.ArgumentMatchers.any(SurgePricingRule.class)))
                .thenReturn(createdRule);

        var created = service.createRule(new SurgePricingRuleCreateRequest(
                VehicleType.MOTORBIKE,
                "Peak",
                3,
                BigDecimal.valueOf(2.00),
                BigDecimal.valueOf(1.30),
                true,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600)
        ));

        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.multiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.30));

        when(surgePricingRuleRepository.findById(10L)).thenReturn(Optional.of(createdRule));
        when(surgePricingRuleRepository.save(createdRule)).thenReturn(createdRule);

        var updated = service.updateRule(10L, new SurgePricingRuleUpdateRequest(
                "Peak night",
                null,
                BigDecimal.valueOf(2.50),
                BigDecimal.valueOf(1.40),
                null,
                null,
                null
        ));

        assertThat(updated.name()).isEqualTo("Peak night");
        assertThat(updated.minDemandSupplyRatio()).isEqualByComparingTo(BigDecimal.valueOf(2.50));
        assertThat(updated.multiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.40));

        var deactivated = service.deactivateRule(10L);

        assertThat(deactivated.active()).isFalse();
        verify(surgePricingRuleRepository, org.mockito.Mockito.times(2)).save(createdRule);
    }

    @Test
    void updateRejectsMissingRule() {
        when(surgePricingRuleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(99L, new SurgePricingRuleUpdateRequest(
                "Missing",
                null,
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.SURGE_PRICING_RULE_NOT_FOUND)
                );
    }

    private PricingConfig pricingConfig(BigDecimal surgeMultiplier) {
        return PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                surgeMultiplier,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private SurgePricingRule rule(
            Long id,
            String name,
            BigDecimal minDemandSupplyRatio,
            BigDecimal multiplier
    ) {
        SurgePricingRule rule = SurgePricingRule.create(
                VehicleType.MOTORBIKE,
                name,
                2,
                minDemandSupplyRatio,
                multiplier,
                true,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600)
        );
        ReflectionTestUtils.setField(rule, "id", id);
        ReflectionTestUtils.setField(rule, "createdAt", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(rule, "updatedAt", NOW.minusSeconds(60));
        return rule;
    }
}