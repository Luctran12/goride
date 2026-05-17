package com.example.goride.user.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTests {
    @Test
    void createNormalizesBasicFields() {
        User user = User.create(
                " Nguyen Van A ",
                " 0901234567 ",
                " a@example.com ",
                " encoded-password ",
                Set.of(UserRole.PASSENGER)
        );

        assertThat(user.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(user.getPhone()).isEqualTo("0901234567");
        assertThat(user.getEmail()).isEqualTo("a@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).containsExactly(UserRole.PASSENGER);
    }

    @Test
    void createRequiresAtLeastOneRole() {
        assertThatThrownBy(() -> User.create("Nguyen Van A", "0901234567", null, "hash", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User must have at least one role");
    }

    @Test
    void canAddRoleAndSoftDelete() {
        User user = User.create("Nguyen Van A", "0901234567", null, "hash", Set.of(UserRole.PASSENGER));

        user.addRole(UserRole.DRIVER);
        user.softDelete();

        assertThat(user.hasRole(UserRole.DRIVER)).isTrue();
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }
}
