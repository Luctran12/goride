package com.example.goride.user.service;

import com.example.goride.user.domain.User;
import com.example.goride.user.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getPhone(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getStatus(),
                Set.copyOf(user.getRoles()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
