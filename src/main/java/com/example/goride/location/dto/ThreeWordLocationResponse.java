package com.example.goride.location.dto;

import java.math.BigDecimal;
import java.util.List;

public record ThreeWordLocationResponse(
        BigDecimal lat,
        BigDecimal lng,
        List<String> words,
        String wordAddress,
        ThreeWordCellBoundsResponse bounds
) {
}
