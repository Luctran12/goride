package com.example.goride.location.provider;

import java.math.BigDecimal;
import java.util.List;

public interface ThreeWordLocationClient {
    ProviderLocation toWords(
            String baseUrl,
            BigDecimal latitude,
            BigDecimal longitude
    );

    ProviderLocation toCoordinate(
            String baseUrl,
            String address
    );

    record ProviderLocation(
            BigDecimal lat,
            BigDecimal lon,
            List<String> words,
            String address,
            ProviderCellBounds bounds
    ) {
    }

    record ProviderCellBounds(
            List<BigDecimal> sw,
            List<BigDecimal> ne
    ) {
    }
}
