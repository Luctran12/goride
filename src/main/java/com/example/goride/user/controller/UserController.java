package com.example.goride.user.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.PageResponse;
import com.example.goride.user.dto.UserCreateRequest;
import com.example.goride.user.dto.UserResponse;
import com.example.goride.user.dto.UserUpdateRequest;
import com.example.goride.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(userService.createUser(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUserById(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(userService.getAllUsers(page, size));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok();
    }
}
