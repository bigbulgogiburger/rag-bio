package com.biorad.csrag.infrastructure.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_roles")
public class UserRoleJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserJpaEntity user;

    @Column(nullable = false, length = 50)
    private String role;

    protected UserRoleJpaEntity() {}

    public UserRoleJpaEntity(UUID id, AppUserJpaEntity user, String role) {
        this.id = id;
        this.user = user;
        this.role = role;
    }

    public UUID getId() { return id; }
    public AppUserJpaEntity getUser() { return user; }
    public String getRole() { return role; }
}
