package com.tally.repository;

import com.tally.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for User entities.
 *
 * Provides automatic CRUD operations without writing implementation code.
 * JpaRepository gives us methods like:
 * - save(User) - Create or update
 * - findById(Long) - Find by primary key
 * - findAll() - Get all users
 * - deleteById(Long) - Delete by primary key
 * - count() - Count total users
 *
 * Custom query methods are derived from method names.
 * Spring Data JPA automatically generates the SQL.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by username (case-sensitive).
     * Returns Optional.empty() if not found.
     *
     * Spring Data JPA query derivation:
     * - "findBy" → SELECT ... FROM users WHERE
     * - "Username" → username = ?
     *
     * Generated SQL: SELECT * FROM users WHERE username = ?
     */
    Optional<User> findByUsername(String username);

    /**
     * Find a user by email (case-sensitive).
     * Returns Optional.empty() if not found.
     *
     * Generated SQL: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given username.
     * More efficient than findByUsername when you only need existence check.
     *
     * Generated SQL: SELECT COUNT(*) > 0 FROM users WHERE username = ?
     */
    boolean existsByUsername(String username);

    /**
     * Check if a user exists with the given email.
     * More efficient than findByEmail when you only need existence check.
     *
     * Generated SQL: SELECT COUNT(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);
}
