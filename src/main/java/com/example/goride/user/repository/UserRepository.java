package com.example.goride.user.repository;

import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    long countByStatusAndDeletedAtIsNull(UserStatus status);
}
