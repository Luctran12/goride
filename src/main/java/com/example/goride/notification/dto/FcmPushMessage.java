package com.example.goride.notification.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record FcmPushMessage(
        String token,
        String title,
        String body,
        Map<String, String> data
) {
    public FcmPushMessage {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("FCM token is required");
        }
        token = token.strip();
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static FcmPushMessage from(String token, UserNotification notification) {
        return new FcmPushMessage(
                token,
                notification.title(),
                notification.body(),
                stringifyData(notification.data())
        );
    }

    private static Map<String, String> stringifyData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }

        return data.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.valueOf(entry.getValue()),
                        (first, second) -> second,
                        LinkedHashMap::new
                ));
    }
}
