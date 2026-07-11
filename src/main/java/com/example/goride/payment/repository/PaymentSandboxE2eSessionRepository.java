package com.example.goride.payment.repository;

import com.example.goride.payment.domain.PaymentSandboxE2eSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentSandboxE2eSessionRepository extends JpaRepository<PaymentSandboxE2eSession, Long> {
    List<PaymentSandboxE2eSession> findAllByOrderByTestedAtDescCreatedAtDesc();

    List<PaymentSandboxE2eSession> findByProviderNameOrderByTestedAtDescCreatedAtDesc(String providerName);
}