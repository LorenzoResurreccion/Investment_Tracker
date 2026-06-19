package com.investmenttracker.user;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user account operations.
 *
 * Mapped to /api/users. Resolves the authenticated user from the request
 * attribute set by {@code UserResolutionFilter} and delegates to
 * {@link UserService}.
 *
 * Requirements: 8.3, 8.4, 8.5
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Permanently deletes the authenticated user's account and all associated data.
     *
     * Resolves the user from the request attribute (set by UserResolutionFilter),
     * then delegates to UserService.deleteUser for transactional cascade deletion.
     *
     * @param request the HTTP request (contains authenticatedUser attribute)
     * @return 204 No Content on successful deletion
     *
     * Requirements: 8.3, 8.4, 8.5
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        log.info("UserController: DELETE /api/users/me requested by user='{}'", user.getUsername());

        userService.deleteUser(user);

        return ResponseEntity.noContent().build();
    }
}
