package com.vetclinic.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "user_account", indexes = @Index(name = "idx_user_username", columnList = "username", unique = true))
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Column(nullable = false)
    private String displayName;
    private Long ownerId;
    private Long veterinarianId;

    protected UserAccount() {}

    public UserAccount(String username, String passwordHash, Role role, String displayName, Long ownerId, Long veterinarianId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
        this.ownerId = ownerId;
        this.veterinarianId = veterinarianId;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public Long getOwnerId() { return ownerId; }
    public Long getVeterinarianId() { return veterinarianId; }
}
