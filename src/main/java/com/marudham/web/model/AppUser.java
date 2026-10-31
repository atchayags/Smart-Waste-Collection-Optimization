package com.marudham.web.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false, length = 20) private String role;
    private String fullName;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public AppUser() {}
    public AppUser(String username, String passwordHash, String role, String fullName) {
        this.username = username; this.passwordHash = passwordHash; this.role = role; this.fullName = fullName;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getFullName() { return fullName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
