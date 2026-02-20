package com.biorad.csrag.infrastructure.persistence.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserJpaEntityTest {

    @Test
    void constructor_setsAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AppUserJpaEntity user = new AppUserJpaEntity(
                id, "admin", "$2a$10$hash", "Admin User",
                "admin@biorad.com", true, now, now
        );

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(user.getDisplayName()).isEqualTo("Admin User");
        assertThat(user.getEmail()).isEqualTo("admin@biorad.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void getRoles_emptyByDefault() {
        AppUserJpaEntity user = new AppUserJpaEntity(
                UUID.randomUUID(), "user", "hash", "User", "user@test.com",
                true, Instant.now(), Instant.now()
        );
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    void getRoleNames_returnsRoleStrings() {
        AppUserJpaEntity user = new AppUserJpaEntity(
                UUID.randomUUID(), "user", "hash", "User", "user@test.com",
                true, Instant.now(), Instant.now()
        );
        user.getRoles().add(new UserRoleJpaEntity(UUID.randomUUID(), user, "ADMIN"));
        user.getRoles().add(new UserRoleJpaEntity(UUID.randomUUID(), user, "REVIEWER"));

        Set<String> roleNames = user.getRoleNames();
        assertThat(roleNames).containsExactlyInAnyOrder("ADMIN", "REVIEWER");
    }

    @Test
    void getRoleNames_empty_returnsEmptySet() {
        AppUserJpaEntity user = new AppUserJpaEntity(
                UUID.randomUUID(), "user", "hash", null, null,
                false, Instant.now(), Instant.now()
        );
        assertThat(user.getRoleNames()).isEmpty();
    }

    @Test
    void userRoleEntity_accessors() {
        AppUserJpaEntity user = new AppUserJpaEntity(
                UUID.randomUUID(), "user", "hash", "User", null,
                true, Instant.now(), Instant.now()
        );
        UUID roleId = UUID.randomUUID();
        UserRoleJpaEntity role = new UserRoleJpaEntity(roleId, user, "SENDER");

        assertThat(role.getId()).isEqualTo(roleId);
        assertThat(role.getUser()).isEqualTo(user);
        assertThat(role.getRole()).isEqualTo("SENDER");
    }

    @Test
    void disabledUser() {
        AppUserJpaEntity user = new AppUserJpaEntity(
                UUID.randomUUID(), "disabled", "hash", "Disabled", null,
                false, Instant.now(), Instant.now()
        );
        assertThat(user.isEnabled()).isFalse();
    }
}
