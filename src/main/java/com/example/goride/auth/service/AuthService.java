package com.example.goride.auth.service;

import com.example.goride.auth.dto.AuthResponse;
import com.example.goride.auth.dto.LoginRequest;
import com.example.goride.auth.dto.LogoutRequest;
import com.example.goride.auth.dto.RefreshTokenRequest;
import com.example.goride.auth.dto.RegisterRequest;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.domain.UserStatus;
import com.example.goride.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Set<UserRole> roles = Set.copyOf(request.roles());
        String phone = request.phone().trim();
        String email = normalizeOptional(request.email());

        if (roles.contains(UserRole.ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Public registration cannot create admin users");
        }
        if (userRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        if (email != null && userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(
                request.fullName(),
                phone,
                email,
                passwordEncoder.encode(request.password()),
                roles
        );
        User savedUser = userRepository.save(user);
        return issueTokens(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneAndDeletedAtIsNull(request.phone().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User account is not active");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.RefreshTokenClaims claims = refreshTokenService.validate(request.refreshToken());
        User user = userRepository.findByIdAndDeletedAtIsNull(claims.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User account is not active");
        }

        refreshTokenService.revoke(request.refreshToken());
        return issueTokens(user);
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                user.getId(),
                jwtTokenService.createAccessToken(user),
                refreshTokenService.createRefreshToken(user),
                user.getRoles()
        );
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
