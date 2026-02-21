package com.tally.service;

import com.tally.dto.request.LoginRequest;
import com.tally.dto.request.RefreshTokenRequest;
import com.tally.dto.request.RegisterRequest;
import com.tally.dto.response.AuthResponse;
import com.tally.dto.response.UserResponse;
import com.tally.exception.EmailAlreadyExistsException;
import com.tally.exception.InvalidCredentialsException;
import com.tally.exception.InvalidTokenException;
import com.tally.exception.UsernameAlreadyExistsException;
import com.tally.model.User;
import com.tally.repository.UserRepository;
import com.tally.security.CustomUserDetails;
import com.tally.security.JwtTokenProvider;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getUsername(), request.getEmail(), hashedPassword);
        user = userRepository.save(user);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        UserResponse userResponse = mapToUserResponse(user);
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiration() / 1000,
                userResponse
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticateUser(LoginRequest request) {
        String usernameOrEmail = request.getUsernameOrEmail();

        // Try username first, then fall back to email lookup.
        // We use the same generic InvalidCredentialsException for both "not found"
        // and "wrong password" to avoid leaking which accounts exist.
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        UserResponse userResponse = mapToUserResponse(user);
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiration() / 1000,
                userResponse
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            boolean isValid = jwtTokenProvider.validateToken(request.getRefreshToken());
            if (!isValid) {
                throw new InvalidTokenException("Invalid refresh token");
            }

            String username = jwtTokenProvider.getUsernameFromToken(request.getRefreshToken());
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            CustomUserDetails userDetails = new CustomUserDetails(user);
            String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

            UserResponse userResponse = mapToUserResponse(user);
            return new AuthResponse(
                    newAccessToken,
                    newRefreshToken,
                    jwtTokenProvider.getAccessTokenExpiration() / 1000,
                    userResponse
            );
        } catch (InvalidTokenException ex) {
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
