package com.example.goride.booking.service;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.SurgePricingRule;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.dto.FareSurgeResponse;
import com.example.goride.booking.dto.SurgePricingRuleCreateRequest;
import com.example.goride.booking.dto.SurgePricingRuleResponse;
import com.example.goride.booking.dto.SurgePricingRuleUpdateRequest;
import com.example.goride.booking.repository.SurgePricingRuleRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.repository.DriverProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class SurgePricingService {
    private static final BigDecimal DEFAULT_DYNAMIC_MULTIPLIER = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);

    private final SurgePricingRuleRepository surgePricingRuleRepository;
    private final TripRepository tripRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final Clock clock;

    public SurgePricingService(
            SurgePricingRuleRepository surgePricingRuleRepository,
            TripRepository tripRepository,
            DriverProfileRepository driverProfileRepository,
            Clock clock
    ) {
        this.surgePricingRuleRepository = surgePricingRuleRepository;
        this.tripRepository = tripRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SurgePricingRuleResponse> listRules() {
        return surgePricingRuleRepository.findAllByOrderByVehicleTypeAscActiveDescMinDemandSupplyRatioAscMultiplierAsc()
                .stream()
                .map(SurgePricingRuleResponse::from)
                .toList();
    }

    @Transactional
    public SurgePricingRuleResponse createRule(SurgePricingRuleCreateRequest request) {
        SurgePricingRule rule = SurgePricingRule.create(
                request.vehicleType(),
                request.name(),
                request.minDemandTrips(),
                request.minDemandSupplyRatio(),
                request.multiplier(),
                request.active(),
                request.startsAt(),
                request.endsAt()
        );
        return SurgePricingRuleResponse.from(surgePricingRuleRepository.save(rule));
    }

    @Transactional
    public SurgePricingRuleResponse updateRule(Long ruleId, SurgePricingRuleUpdateRequest request) {
        SurgePricingRule rule = getRule(ruleId);
        rule.update(
                request.name(),
                request.minDemandTrips(),
                request.minDemandSupplyRatio(),
                request.multiplier(),
                request.active(),
                request.startsAt(),
                request.endsAt()
        );
        return SurgePricingRuleResponse.from(surgePricingRuleRepository.save(rule));
    }

    @Transactional
    public SurgePricingRuleResponse deactivateRule(Long ruleId) {
        SurgePricingRule rule = getRule(ruleId);
        rule.deactivate();
        return SurgePricingRuleResponse.from(surgePricingRuleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public FareSurgeResponse currentStatus(VehicleType vehicleType) {
        return quote(vehicleType, BigDecimal.ONE, false).toResponse();
    }

    @Transactional(readOnly = true)
    public SurgePricingQuote quote(PricingConfig pricingConfig, boolean includePendingRequest) {
        return quote(pricingConfig.getVehicleType(), pricingConfig.getSurgeMultiplier(), includePendingRequest);
    }

    private SurgePricingQuote quote(
            VehicleType vehicleType,
            BigDecimal pricingSurgeMultiplier,
            boolean includePendingRequest
    ) {
        long persistedDemand = tripRepository.countByVehicleTypeAndStatusAndDeletedAtIsNull(
                vehicleType,
                TripStatus.SEARCHING
        );
        long demandTrips = persistedDemand + (includePendingRequest ? 1 : 0);
        long onlineDrivers = driverProfileRepository.countOnlineApprovedByVehicleType(vehicleType);
        BigDecimal demandSupplyRatio = demandSupplyRatio(demandTrips, onlineDrivers);
        Instant now = clock.instant();
        Optional<SurgePricingRule> matchedRule = surgePricingRuleRepository
                .findByVehicleTypeAndActiveTrueOrderByMinDemandSupplyRatioDescMultiplierDesc(vehicleType)
                .stream()
                .filter(rule -> rule.matches(demandTrips, demandSupplyRatio, now))
                .max(Comparator
                        .comparing(SurgePricingRule::getMinDemandSupplyRatio)
                        .thenComparing(SurgePricingRule::getMultiplier));

        BigDecimal dynamicMultiplier = matchedRule
                .map(SurgePricingRule::getMultiplier)
                .map(SurgePricingService::normalizeMultiplier)
                .orElse(DEFAULT_DYNAMIC_MULTIPLIER);
        BigDecimal normalizedPricingMultiplier = normalizeMultiplier(pricingSurgeMultiplier);
        BigDecimal effectiveMultiplier = normalizeMultiplier(normalizedPricingMultiplier.multiply(dynamicMultiplier));

        return new SurgePricingQuote(
                vehicleType,
                demandTrips,
                onlineDrivers,
                demandSupplyRatio,
                normalizedPricingMultiplier,
                dynamicMultiplier,
                effectiveMultiplier,
                matchedRule.map(SurgePricingRule::getId).orElse(null),
                matchedRule.map(SurgePricingRule::getName).orElse(null)
        );
    }

    private SurgePricingRule getRule(Long ruleId) {
        return surgePricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SURGE_PRICING_RULE_NOT_FOUND));
    }

    private static BigDecimal demandSupplyRatio(long demandTrips, long onlineDrivers) {
        if (demandTrips <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long denominator = Math.max(onlineDrivers, 1L);
        return BigDecimal.valueOf(demandTrips)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeMultiplier(BigDecimal multiplier) {
        return multiplier.setScale(2, RoundingMode.HALF_UP);
    }

    public record SurgePricingQuote(
            VehicleType vehicleType,
            long demandTrips,
            long onlineDrivers,
            BigDecimal demandSupplyRatio,
            BigDecimal pricingSurgeMultiplier,
            BigDecimal dynamicSurgeMultiplier,
            BigDecimal effectiveSurgeMultiplier,
            Long ruleId,
            String ruleName
    ) {
        public boolean surgeApplied() {
            return effectiveSurgeMultiplier.compareTo(BigDecimal.ONE) > 0;
        }

        public FareSurgeResponse toResponse() {
            return new FareSurgeResponse(
                    vehicleType,
                    demandTrips,
                    onlineDrivers,
                    demandSupplyRatio,
                    pricingSurgeMultiplier,
                    dynamicSurgeMultiplier,
                    effectiveSurgeMultiplier,
                    surgeApplied(),
                    ruleId,
                    ruleName
            );
        }
    }
}