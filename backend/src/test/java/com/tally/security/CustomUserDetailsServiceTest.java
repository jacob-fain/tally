package com.tally.security;

import com.tally.model.User;
import com.tally.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("testuser", "test@example.com", "$2a$10$hashedPassword");
    }

    @Test
    void loadUserByUsername_ExistingUser_ReturnsCustomUserDetails() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");

        assertNotNull(userDetails);
        assertTrue(userDetails instanceof CustomUserDetails);
        assertEquals("testuser", userDetails.getUsername());
        assertEquals("$2a$10$hashedPassword", userDetails.getPassword());
        verify(userRepository, times(1)).findByUsername("testuser");
    }

    @Test
    void loadUserByUsername_NonExistentUser_ThrowsUsernameNotFoundException() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("nonexistent"));
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    void loadUserByUsername_LoadsCorrectUserData() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");
        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;

        assertEquals(testUser.getUsername(), customUserDetails.getUser().getUsername());
        assertEquals(testUser.getEmail(), customUserDetails.getUser().getEmail());
        assertEquals(testUser.getPasswordHash(), customUserDetails.getUser().getPasswordHash());
        assertNotNull(customUserDetails.getUser().getCreatedAt());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isCredentialsNonExpired());
    }
}
