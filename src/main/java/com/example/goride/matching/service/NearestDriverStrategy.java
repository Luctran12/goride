package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class NearestDriverStrategy implements DriverMatchingStrategy {
    @Override
    public List<DriverCandidate> rank(MatchingRequest request, List<DriverCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingLong(DriverCandidate::distanceMeters)
                        .thenComparing(DriverCandidate::driverId))
                .limit(request.limit())
                .toList();
    }
}
