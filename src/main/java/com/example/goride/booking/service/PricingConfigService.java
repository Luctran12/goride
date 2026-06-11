package com.example.goride.booking.service;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.dto.PricingConfigCreateRequest;
import com.example.goride.booking.dto.PricingConfigResponse;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PricingConfigService {
    private final PricingConfigRepository pricingConfigRepository;

    public PricingConfigService(PricingConfigRepository pricingConfigRepository) {
        this.pricingConfigRepository = pricingConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<PricingConfigResponse> listActivePricing() {
        return pricingConfigRepository.findByActiveTrueOrderByVehicleTypeAscEffectiveFromDesc()
                .stream()
                .map(PricingConfigResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PricingConfigResponse> listAllPricing() {
        return pricingConfigRepository.findAllByOrderByVehicleTypeAscEffectiveFromDesc()
                .stream()
                .map(PricingConfigResponse::from)
                .toList();
    }

    @Transactional
    public PricingConfigResponse createPricing(PricingConfigCreateRequest request) {
        validateCreateRequest(request);
        pricingConfigRepository.findByVehicleTypeAndActiveTrue(request.vehicleType())
                .forEach(PricingConfig::deactivate);

        PricingConfig pricingConfig = PricingConfig.create(
                request.vehicleType(),
                request.baseFare(),
                request.perKmRate(),
                request.perMinuteRate(),
                request.minimumFare(),
                request.surgeMultiplier(),
                request.effectiveFrom()
        );
        return PricingConfigResponse.from(pricingConfigRepository.save(pricingConfig));
    }

    private void validateCreateRequest(PricingConfigCreateRequest request) {
        if (request.effectiveFrom() != null && request.effectiveFrom().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Effective time must not be in the future");
        }
    }

    @Transactional
    public PricingConfigResponse deactivatePricing(Long pricingConfigId) {
        PricingConfig pricingConfig = pricingConfigRepository.findById(pricingConfigId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICING_CONFIG_NOT_FOUND));
        pricingConfig.deactivate();
        return PricingConfigResponse.from(pricingConfigRepository.save(pricingConfig));
    }
}
