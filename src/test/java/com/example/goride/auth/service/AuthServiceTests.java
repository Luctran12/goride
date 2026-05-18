package com.example.goride.auth.service;

import com.example.goride.auth.dto.LoginRequest;
import com.example.goride.auth.dto.RefreshTokenRequest;
import com.example.goride.auth.dto.RegisterRequest;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesUserWithEncodedPasswordAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A",
                "0901234567",
                "a@example.com",
                "password123",
                Set.of(UserRole.PASSENGER)
        );
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        when(jwtTokenService.createAccessToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        var response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRoles()).containsExactly(UserRole.PASSENGER);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void registerRejectsPublicAdminRole() {
        RegisterRequest request = new RegisterRequest(
                "Admin",
                "0901234567",
                null,
                "password123",
                Set.of(UserRole.ADMIN)
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = withId(User.create(
                "Nguyen Van A",
                "0901234567",
                null,
                "encoded-password",
                Set.of(UserRole.PASSENGER)
        ), 1L);
        when(userRepository.findByPhoneAndDeletedAtIsNull("0901234567")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("0901234567", "wrong-password")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
                );
    }

    @Test
    void refreshRevokesOldTokenAndIssuesNewTokens() {
        String oldRefreshToken = "1.token-id.secret";
        User user = withId(User.create(
                "Nguyen Van A",
                "0901234567",
                null,
                "encoded-password",
                Set.of(UserRole.PASSENGER)
        ), 1L);
        when(refreshTokenService.validate(oldRefreshToken))
                .thenReturn(new RefreshTokenService.RefreshTokenClaims(1L, "token-id"));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(jwtTokenService.createAccessToken(user)).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh-token");

        var response = authService.refresh(new RefreshTokenRequest(oldRefreshToken));

        InOrder inOrder = inOrder(refreshTokenService, jwtTokenService);
        inOrder.verify(refreshTokenService).revoke(oldRefreshToken);
        inOrder.verify(jwtTokenService).createAccessToken(user);
        inOrder.verify(refreshTokenService).createRefreshToken(user);
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    private User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
