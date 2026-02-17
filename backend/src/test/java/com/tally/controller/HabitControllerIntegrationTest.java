package com.tally.controller;

import com.tally.dto.request.CreateHabitRequest;
import com.tally.dto.request.LoginRequest;
import com.tally.dto.request.RegisterRequest;
import com.tally.dto.request.ReorderHabitsRequest;
import com.tally.dto.request.UpdateHabitRequest;
import com.tally.dto.response.AuthResponse;
import com.tally.dto.response.HabitResponse;
import com.tally.dto.response.HabitStatsResponse;
import com.tally.dto.response.HeatmapResponse;
import com.tally.repository.HabitRepository;
import com.tally.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HabitControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private UserRepository userRepository;

    private String authToken;

    @BeforeEach
    void setUp() {
        habitRepository.deleteAll();
        userRepository.deleteAll();
        authToken = registerAndGetToken("testuser", "test@example.com", "password123");
    }

    // =========================================================================
    // GET /api/habits
    // =========================================================================

    @Test
    void shouldReturnEmptyListWhenNoHabits() {
        ResponseEntity<List<HabitResponse>> response = getHabits();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void shouldReturnOnlyActiveHabitsByDefault() {
        Long habitId = createHabit("Workout", null, null).id();
        archiveHabit(habitId);
        createHabit("Read", null, null);

        ResponseEntity<List<HabitResponse>> response = getHabits();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Read", response.getBody().get(0).name());
    }

    @Test
    void shouldReturnAllHabitsWhenIncludeArchivedTrue() {
        Long habitId = createHabit("Workout", null, null).id();
        archiveHabit(habitId);
        createHabit("Read", null, null);

        ResponseEntity<List<HabitResponse>> response = authGet(
                "/api/habits?includeArchived=true",
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/habits", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // =========================================================================
    // POST /api/habits
    // =========================================================================

    @Test
    void shouldCreateHabitAndReturn201() {
        CreateHabitRequest request = new CreateHabitRequest("Morning Run", "5km run", "#3498db");
        ResponseEntity<HabitResponse> response = authPost("/api/habits", request, HabitResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertEquals("Morning Run", response.getBody().name());
        assertEquals("5km run", response.getBody().description());
        assertEquals("#3498db", response.getBody().color());
        assertFalse(response.getBody().archived());
    }

    @Test
    void shouldRejectHabitWithBlankName() {
        CreateHabitRequest request = new CreateHabitRequest("", null, null);
        ResponseEntity<String> response = authPost("/api/habits", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldRejectHabitWithInvalidColorFormat() {
        CreateHabitRequest request = new CreateHabitRequest("Valid Name", null, "not-a-color");
        ResponseEntity<String> response = authPost("/api/habits", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // =========================================================================
    // GET /api/habits/{id}
    // =========================================================================

    @Test
    void shouldGetHabitById() {
        HabitResponse created = createHabit("Meditation", null, null);

        ResponseEntity<HabitResponse> response = authGet(
                "/api/habits/" + created.id(), HabitResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(created.id(), response.getBody().id());
        assertEquals("Meditation", response.getBody().name());
    }

    @Test
    void shouldReturn404ForNonExistentHabit() {
        ResponseEntity<String> response = authGet("/api/habits/99999", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldNotAllowAccessingAnotherUsersHabit() {
        HabitResponse created = createHabit("Private Habit", null, null);

        // Register a second user and try to access first user's habit
        String token2 = registerAndGetToken("user2", "user2@example.com", "password123");
        ResponseEntity<String> response = authGetWithToken(
                "/api/habits/" + created.id(), String.class, token2);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // =========================================================================
    // PUT /api/habits/{id}
    // =========================================================================

    @Test
    void shouldUpdateHabit() {
        HabitResponse created = createHabit("Old Name", null, null);
        UpdateHabitRequest update = new UpdateHabitRequest("New Name", "New desc", "#e74c3c");

        ResponseEntity<HabitResponse> response = authPut(
                "/api/habits/" + created.id(), update, HabitResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("New Name", response.getBody().name());
        assertEquals("New desc", response.getBody().description());
        assertEquals("#e74c3c", response.getBody().color());
    }

    // =========================================================================
    // DELETE /api/habits/{id}
    // =========================================================================

    @Test
    void shouldDeleteHabitAndReturn204() {
        HabitResponse created = createHabit("To Delete", null, null);

        ResponseEntity<Void> response = authDelete("/api/habits/" + created.id());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Should no longer appear in active list
        ResponseEntity<List<HabitResponse>> habits = getHabits();
        assertTrue(habits.getBody().isEmpty());
    }

    // =========================================================================
    // PUT /api/habits/{id}/archive
    // =========================================================================

    @Test
    void shouldArchiveHabitAndSetArchivedTrue() {
        HabitResponse created = createHabit("To Archive", null, null);

        ResponseEntity<HabitResponse> response = authPut(
                "/api/habits/" + created.id() + "/archive", null, HabitResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().archived());
        assertNotNull(response.getBody().archivedAt());
    }

    // =========================================================================
    // PUT /api/habits/reorder
    // =========================================================================

    @Test
    void shouldReorderHabits() {
        HabitResponse h1 = createHabit("First", null, null);
        HabitResponse h2 = createHabit("Second", null, null);

        ReorderHabitsRequest request = new ReorderHabitsRequest(List.of(
                new ReorderHabitsRequest.HabitOrderItem(h1.id(), 1),
                new ReorderHabitsRequest.HabitOrderItem(h2.id(), 0)));

        ResponseEntity<Void> response = authPut("/api/habits/reorder", request, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Second habit (displayOrder=0) should now come first
        ResponseEntity<List<HabitResponse>> habits = getHabits();
        assertEquals("Second", habits.getBody().get(0).name());
        assertEquals("First", habits.getBody().get(1).name());
    }

    // =========================================================================
    // GET /api/habits/{id}/stats
    // =========================================================================

    @Test
    void shouldReturnStatsWithZeroStreakForNewHabit() {
        HabitResponse created = createHabit("New Habit", null, null);

        ResponseEntity<HabitStatsResponse> response = authGet(
                "/api/habits/" + created.id() + "/stats", HabitStatsResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().currentStreak());
        assertEquals(0, response.getBody().longestStreak());
        assertEquals(0, response.getBody().totalCompletedDays());
    }

    // =========================================================================
    // GET /api/habits/{id}/heatmap
    // =========================================================================

    @Test
    void shouldReturnHeatmapForFullYear() {
        HabitResponse created = createHabit("Tracked Habit", null, null);

        ResponseEntity<HeatmapResponse> response = authGet(
                "/api/habits/" + created.id() + "/heatmap?year=2026", HeatmapResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(365, response.getBody().days().size()); // 2026 is not a leap year
    }

    @Test
    void shouldReturnHeatmapForMonth() {
        HabitResponse created = createHabit("Tracked Habit", null, null);

        ResponseEntity<HeatmapResponse> response = authGet(
                "/api/habits/" + created.id() + "/heatmap?month=2026-02", HeatmapResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(28, response.getBody().days().size()); // Feb 2026 has 28 days
    }

    @Test
    void shouldReturnHeatmapForCustomDateRange() {
        HabitResponse created = createHabit("Tracked Habit", null, null);

        ResponseEntity<HeatmapResponse> response = authGet(
                "/api/habits/" + created.id() + "/heatmap?startDate=2026-01-01&endDate=2026-01-10",
                HeatmapResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, response.getBody().days().size());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String registerAndGetToken(String username, String email, String password) {
        RegisterRequest req = new RegisterRequest(username, email, password);
        ResponseEntity<AuthResponse> res = restTemplate.postForEntity("/api/auth/register", req, AuthResponse.class);
        return res.getBody().getAccessToken();
    }

    private HabitResponse createHabit(String name, String description, String color) {
        CreateHabitRequest request = new CreateHabitRequest(name, description, color);
        return authPost("/api/habits", request, HabitResponse.class).getBody();
    }

    private void archiveHabit(Long habitId) {
        authPut("/api/habits/" + habitId + "/archive", null, HabitResponse.class);
    }

    private ResponseEntity<List<HabitResponse>> getHabits() {
        return authGet("/api/habits", new ParameterizedTypeReference<>() {});
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeadersWithToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private <T> ResponseEntity<T> authGet(String url, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), responseType);
    }

    private <T> ResponseEntity<T> authGet(String url, ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), responseType);
    }

    private <T> ResponseEntity<T> authGetWithToken(String url, Class<T> responseType, String token) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeadersWithToken(token)), responseType);
    }

    private <T> ResponseEntity<T> authPost(String url, Object body, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), responseType);
    }

    private <T> ResponseEntity<T> authPut(String url, Object body, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()), responseType);
    }

    private ResponseEntity<Void> authDelete(String url) {
        return restTemplate.exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), Void.class);
    }
}
