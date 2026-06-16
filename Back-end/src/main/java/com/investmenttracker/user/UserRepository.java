package com.investmenttracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their AWS Cognito subject identifier.
     * Used during request processing to resolve the authenticated user
     * from the JWT 'sub' claim.
     *
     * @param cognitoSub the Cognito subject identifier
     * @return an Optional containing the user if found
     */
    Optional<User> findByCognitoSub(String cognitoSub);
}
