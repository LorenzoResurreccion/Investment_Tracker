package com.investmenttracker.user;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * JPA entity representing an authenticated user.
 *
 * Each row maps to a user registered via AWS Cognito. The {@code cognitoSub}
 * field stores the unique subject identifier from Cognito's JWT, which is used
 * to resolve the local user record on every authenticated request.
 *
 * Maps to the "users" table created by Flyway migration V3.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name for the user, sourced from Cognito's cognito:username claim. */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** Email address, sourced from Cognito's email claim. */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** AWS Cognito subject identifier (the 'sub' claim from the JWT). */
    @Column(name = "cognito_sub", nullable = false, unique = true)
    private String cognitoSub;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} to the current time before the entity is first persisted. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    // --- Getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public void setCognitoSub(String cognitoSub) {
        this.cognitoSub = cognitoSub;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
