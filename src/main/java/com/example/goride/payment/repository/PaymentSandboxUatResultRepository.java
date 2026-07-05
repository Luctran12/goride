package com.example.goride.payment.repository;

import com.example.goride.payment.domain.PaymentSandboxUatResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentSandboxUatResultRepository extends JpaRepository<PaymentSandboxUatResult, Long> {
    Optional<PaymentSandboxUatResult> findByProviderName(String providerName);
}
