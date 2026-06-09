package com.example.goride.notification.service;

import java.util.Optional;

public interface FcmDeviceTokenStore {
    void saveToken(Long userId, String token);

    void deleteToken(Long userId);

    Optional<String> findToken(Long userId);
}
