package com.example.goride.user.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.domain.UserStatus;
import com.example.goride.user.dto.UserCreateRequest;
import com.example.goride.user.dto.UserPasswordChangeRequest;
import com.example.goride.user.dto.UserProfileUpdateRequest;
import com.example.goride.user.dto.UserUpdateRequest;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, new UserMapper());
    }

    @Test
    void createUserPersistsEncodedActiveUser() {
        UserCreateRequest request = new UserCreateRequest(
                " Nguyen Van A ",
                " 0901234567 ",
                " a@example.com ",
                "password123",
                " https://example.com/avatar.jpg ",
                null,
                Set.of(UserRole.PASSENGER)
        );
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        var response = userService.createUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(savedUser.getPhone()).isEqualTo("0901234567");
        assertThat(savedUser.getEmail()).isEqualTo("a@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void createUserRejectsDuplicatePhone() {
        when(userRepository.existsByPhoneAndDeletedAtIsNull("0901234567")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(createRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS)
                );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserByIdReturnsActiveUser() {
        User user = withId(User.create("Nguyen Van A", "0901234567", null, "hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        var response = userService.getUserById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.phone()).isEqualTo("0901234567");
    }

    @Test
    void getMyProfileReturnsCurrentUserProfile() {
        User user = withId(User.create("Nguyen Van A", "0901234567", null, "hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        var response = userService.getMyProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void getAllUsersUsesOneBasedPaginationAndCreatedAtDescSort() {
        User user = withId(User.create("Nguyen Van A", "0901234567", null, "hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), org.springframework.data.domain.PageRequest.of(1, 10), 25));

        var response = userService.getAllUsers(2, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findByDeletedAtIsNull(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.pagination().page()).isEqualTo(2);
        assertThat(response.pagination().totalItems()).isEqualTo(25);
        assertThat(response.pagination().totalPages()).isEqualTo(3);
    }

    @Test
    void updateUserChangesProfileRolesStatusAndPassword() {
        User user = withId(User.create("Old Name", "0901234567", "old@example.com", "old-hash", Set.of(UserRole.PASSENGER)), 1L);
        UserUpdateRequest request = new UserUpdateRequest(
                "New Name",
                "0907654321",
                "new@example.com",
                "newpassword",
                "https://example.com/new-avatar.jpg",
                UserStatus.SUSPENDED,
                Set.of(UserRole.PASSENGER, UserRole.DRIVER)
        );
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.updateUser(1L, request);

        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.phone()).isEqualTo("0907654321");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(response.roles()).containsExactlyInAnyOrder(UserRole.PASSENGER, UserRole.DRIVER);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void updateUserDoesNotCheckDuplicatesWhenPhoneAndEmailAreUnchanged() {
        User user = withId(User.create("Name", "0901234567", "a@example.com", "hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateUser(1L, new UserUpdateRequest(
                "Name",
                " 0901234567 ",
                " a@example.com ",
                null,
                null,
                UserStatus.ACTIVE,
                Set.of(UserRole.PASSENGER)
        ));

        verify(userRepository, never()).existsByPhoneAndDeletedAtIsNull(any());
        verify(userRepository, never()).existsByEmailAndDeletedAtIsNull(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateMyProfileChangesOnlyEditableProfileFields() {
        User user = withId(User.create(
                "Old Name",
                "0901234567",
                "old@example.com",
                "old-hash",
                Set.of(UserRole.PASSENGER)
        ), 1L);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "New Name",
                "0907654321",
                "new@example.com",
                "https://example.com/avatar.jpg"
        );
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.updateMyProfile(1L, request);

        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.phone()).isEqualTo("0907654321");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/avatar.jpg");
        assertThat(response.roles()).containsExactly(UserRole.PASSENGER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateMyProfileRejectsDuplicateEmail() {
        User user = withId(User.create("Name", "0901234567", "old@example.com", "hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndDeletedAtIsNull("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateMyProfile(
                1L,
                new UserProfileUpdateRequest("Name", "0901234567", "taken@example.com", null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS)
                );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeMyPasswordRequiresCurrentPasswordAndStoresEncodedNewPassword() {
        User user = withId(User.create("Name", "0901234567", null, "old-hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        userService.changeMyPassword(1L, new UserPasswordChangeRequest("old-password", "new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void changeMyPasswordRejectsIncorrectCurrentPassword() {
        User user = withId(User.create("Name", "0901234567", null, "old-hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeMyPassword(
                1L,
                new UserPasswordChangeRequest("wrong-password", "new-password")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
                );
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteUserSoftDeletesExistingUser() {
        User user = withId(User.create("Name", "0901234567", null, "hash", Set.of(UserRole.PASSENGER)), 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void getUserByIdRejectsMissingUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND)
                );
    }

    private UserCreateRequest createRequest() {
        return new UserCreateRequest(
                "Nguyen Van A",
                "0901234567",
                "a@example.com",
                "password123",
                null,
                UserStatus.ACTIVE,
                Set.of(UserRole.PASSENGER)
        );
    }

    private User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
