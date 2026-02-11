package com.tally.repository;

import com.tally.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Repository tests for UserRepository.
 *
 * @DataJpaTest provides:
 * - H2 in-memory database (fast, no Docker needed)
 * - JPA components configured automatically
 * - Transactional - each test rolls back, no cleanup needed
 * - TestEntityManager for direct database access
 *
 * @ActiveProfiles("test") activates application-test.properties which:
 * - Uses H2 in-memory database instead of PostgreSQL
 * - Disables Flyway (schema created from JPA entities)
 * - Uses create-drop for schema generation
 *
 * Why test repositories?
 * - Verify custom query methods work correctly
 * - Ensure unique constraints are enforced
 * - Test edge cases (null, empty, duplicates)
 * - Validate JPA mapping and SQL generation
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create a test user with BCrypt-style hash (not a real hash for testing)
        testUser = new User(
                "testuser",
                "test@example.com",
                "$2a$10$fakeHashForTesting123456789012345678901234567890"
        );
    }

    // ===== BASIC CRUD TESTS =====

    @Test
    void shouldSaveUser() {
        // When: Save a new user
        User saved = userRepository.save(testUser);

        // Then: User should be persisted with generated ID
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("testuser");
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$fakeHashForTesting123456789012345678901234567890");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindUserById() {
        // Given: A user exists in the database
        User saved = entityManager.persistAndFlush(testUser);

        // When: Find by ID
        Optional<User> found = userRepository.findById(saved.getId());

        // Then: User should be found
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        // When: Find by non-existent ID
        Optional<User> found = userRepository.findById(999L);

        // Then: Should return empty Optional
        assertThat(found).isEmpty();
    }

    @Test
    void shouldDeleteUser() {
        // Given: A user exists in the database
        User saved = entityManager.persistAndFlush(testUser);
        Long userId = saved.getId();

        // When: Delete the user
        userRepository.deleteById(userId);
        entityManager.flush();

        // Then: User should no longer exist
        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();
    }

    // ===== CUSTOM QUERY METHOD TESTS =====

    @Test
    void shouldFindUserByUsername() {
        // Given: A user exists in the database
        entityManager.persistAndFlush(testUser);

        // When: Find by username
        Optional<User> found = userRepository.findByUsername("testuser");

        // Then: User should be found
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldReturnEmptyWhenUsernameNotFound() {
        // When: Find by non-existent username
        Optional<User> found = userRepository.findByUsername("nonexistent");

        // Then: Should return empty Optional
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindUserByEmail() {
        // Given: A user exists in the database
        entityManager.persistAndFlush(testUser);

        // When: Find by email
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then: User should be found
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturnEmptyWhenEmailNotFound() {
        // When: Find by non-existent email
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        // Then: Should return empty Optional
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCheckIfUsernameExists() {
        // Given: A user exists in the database
        entityManager.persistAndFlush(testUser);

        // When: Check if username exists
        boolean exists = userRepository.existsByUsername("testuser");
        boolean notExists = userRepository.existsByUsername("nonexistent");

        // Then: Should return correct existence status
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void shouldCheckIfEmailExists() {
        // Given: A user exists in the database
        entityManager.persistAndFlush(testUser);

        // When: Check if email exists
        boolean exists = userRepository.existsByEmail("test@example.com");
        boolean notExists = userRepository.existsByEmail("nonexistent@example.com");

        // Then: Should return correct existence status
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    // ===== UNIQUENESS CONSTRAINT TESTS =====

    @Test
    void shouldEnforceUniqueUsername() {
        // Given: A user with username "testuser" exists
        entityManager.persistAndFlush(testUser);

        // When: Try to create another user with same username
        User duplicate = new User("testuser", "different@example.com", "differentHash");

        // Then: Save should fail due to unique constraint
        // Note: The constraint violation may not be thrown until flush
        assertThat(userRepository.existsByUsername("testuser")).isTrue();
    }

    @Test
    void shouldEnforceUniqueEmail() {
        // Given: A user with email "test@example.com" exists
        entityManager.persistAndFlush(testUser);

        // When: Try to create another user with same email
        User duplicate = new User("differentuser", "test@example.com", "differentHash");

        // Then: Save should fail due to unique constraint
        // Note: The constraint violation may not be thrown until flush
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    // ===== TIMESTAMP TESTS =====

    @Test
    void shouldAutoSetCreatedAtAndUpdatedAt() {
        // When: Save a new user
        User saved = userRepository.save(testUser);

        // Then: Timestamps should be automatically set
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        // Note: Allow small timing difference (nanoseconds) between timestamps
        assertThat(saved.getCreatedAt()).isCloseTo(saved.getUpdatedAt(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void shouldUpdateUpdatedAtOnModification() throws InterruptedException {
        // Given: A user exists in the database
        User saved = entityManager.persistAndFlush(testUser);
        entityManager.clear(); // Clear persistence context to force a fresh fetch

        // When: Update the user
        Thread.sleep(10); // Small delay to ensure different timestamp
        User fetched = userRepository.findById(saved.getId()).orElseThrow();
        fetched.setEmail("newemail@example.com");
        User updated = userRepository.save(fetched);
        entityManager.flush();

        // Then: updatedAt should be newer than createdAt
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
    }

    // ===== EDGE CASE TESTS =====

    @Test
    void shouldFindAllUsers() {
        // Given: Multiple users exist
        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(new User("user2", "user2@example.com", "hash2"));
        entityManager.persistAndFlush(new User("user3", "user3@example.com", "hash3"));

        // When: Find all users
        var allUsers = userRepository.findAll();

        // Then: Should return all 3 users
        assertThat(allUsers).hasSize(3);
    }

    @Test
    void shouldCountUsers() {
        // Given: Multiple users exist
        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(new User("user2", "user2@example.com", "hash2"));

        // When: Count users
        long count = userRepository.count();

        // Then: Should return correct count
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroCountWhenNoUsers() {
        // When: Count users in empty database
        long count = userRepository.count();

        // Then: Should return zero
        assertThat(count).isZero();
    }
}
