package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PaymentAccessService {
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public PaymentAccessService(PaymentRepository paymentRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Payment requireTripPayment(Long currentUserId, Long tripId) {
        User currentUser = userRepository.findByIdAndDeletedAtIsNull(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Payment payment = paymentRepository.findByTripIdWithTrip(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        assertCanAccessPayment(currentUser, payment.getTrip());
        return payment;
    }

    private void assertCanAccessPayment(User user, Trip trip) {
        if (user.hasRole(UserRole.ADMIN)
                || Objects.equals(user.getId(), trip.getPassenger().getId())
                || (trip.getDriver() != null && Objects.equals(user.getId(), trip.getDriver().getId()))) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
