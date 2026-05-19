package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;

import java.util.List;

public interface DriverMatchingStrategy {
    List<DriverCandidate> rank(MatchingRequest request, List<DriverCandidate> candidates);
}
