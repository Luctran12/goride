package com.example.goride.user.service;

import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.domain.UserStatus;
import com.example.goride.user.dto.UserCreateRequest;
import com.example.goride.user.dto.UserResponse;
import com.example.goride.user.dto.UserUpdateRequest;
import com.example.goride.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        String phone = normalizeRequired(request.phone(), "phone");
        String email = normalizeOptional(request.email());
        validateUniquePhone(phone);
        validateUniqueEmail(email);

        User user = User.create(
                request.fullName(),
                phone,
                email,
                passwordEncoder.encode(request.password()),
                roles(request.roles())
        );
        user.updateDetails(user.getFullName(), user.getPhone(), user.getEmail(), request.avatarUrl());
        user.changeStatus(request.status() == null ? UserStatus.ACTIVE : request.status());

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<User> users = userRepository.findByDeletedAtIsNull(pageRequest);

        return PageResponse.of(
                users.getContent().stream().map(userMapper::toResponse).toList(),
                normalizedPage,
                normalizedSize,
                users.getTotalElements()
        );
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String phone = normalizeRequired(request.phone(), "phone");
        String email = normalizeOptional(request.email());
        validateUniquePhoneForUpdate(phone, user.getPhone());
        validateUniqueEmailForUpdate(email, user.getEmail());

        user.updateDetails(request.fullName(), phone, email, request.avatarUrl());
        user.replaceRoles(roles(request.roles()));
        user.changeStatus(request.status());
        if (request.password() != null && !request.password().isBlank()) {
            user.changePasswordHash(passwordEncoder.encode(request.password()));
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.softDelete();
        userRepository.save(user);
    }

    private void validateUniquePhone(String phone) {
        if (userRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
    }

    private void validateUniqueEmail(String email) {
        if (email != null && userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void validateUniquePhoneForUpdate(String requestedPhone, String currentPhone) {
        if (!Objects.equals(requestedPhone, currentPhone)) {
            validateUniquePhone(requestedPhone);
        }
    }

    private void validateUniqueEmailForUpdate(String requestedEmail, String currentEmail) {
        if (!Objects.equals(requestedEmail, currentEmail)) {
            validateUniqueEmail(requestedEmail);
        }
    }

    private Set<UserRole> roles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "User must have at least one role");
        }
        return Set.copyOf(roles);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
