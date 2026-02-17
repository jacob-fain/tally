package com.tally.controller;

import com.tally.dto.request.BatchDailyLogRequest;
import com.tally.dto.request.CreateDailyLogRequest;
import com.tally.dto.request.CreateHabitRequest;
import com.tally.dto.request.RegisterRequest;
import com.tally.dto.response.AuthResponse;
import com.tally.dto.response.DailyLogResponse;
import com.tally.dto.response.HabitResponse;
import com.tally.repository.DailyLogRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DailyLogControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private UserRepository userRepository;

    private String authToken;
    private Long habitId;

    @BeforeEach
    void setUp() {
        dailyLogRepository.deleteAll();
        habitRepository.deleteAll();
        userRepository.deleteAll();
        authToken = registerAndGetToken("testuser", "test@example.com", "password123");
        habitId = createHabit("Morning Workout").id();
    }

    // =========================================================================
    // POST /api/logs (createOrUpdateLog)
    // =========================================================================

    @Test
    void shouldCreateLogAndReturn201() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        CreateDailyLogRequest request = new CreateDailyLogRequest(habitId, logDate, true, "great session");

        ResponseEntity<DailyLogResponse> response = authPost("/api/logs", request, DailyLogResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertEquals(habitId, response.getBody().habitId());
        assertEquals(logDate, response.getBody().logDate());
        assertTrue(response.getBody().completed());
        assertEquals("great session", response.getBody().notes());
    }

    @Test
    void shouldUpdateExistingLogWhenSameDatePostedTwice() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        CreateDailyLogRequest first = new CreateDailyLogRequest(habitId, logDate, true, "first note");
        CreateDailyLogRequest second = new CreateDailyLogRequest(habitId, logDate, false, "updated note");

        authPost("/api/logs", first, DailyLogResponse.class);
        ResponseEntity<DailyLogResponse> response = authPost("/api/logs", second, DailyLogResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertFalse(response.getBody().completed());
        assertEquals("updated note", response.getBody().notes());

        // Verify only one log exists (upsert, not duplicate)
        ResponseEntity<List<DailyLogResponse>> logs = getLogs(habitId, logDate, logDate);
        assertEquals(1, logs.getBody().size());
    }

    @Test
    void shouldRejectLogWithFutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        CreateDailyLogRequest request = new CreateDailyLogRequest(habitId, futureDate, true, null);

        ResponseEntity<String> response = authPost("/api/logs", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldRejectLogForAnotherUsersHabit() {
        String token2 = registerAndGetToken("user2", "user2@example.com", "password123");
        LocalDate logDate = LocalDate.now().minusDays(1);
        CreateDailyLogRequest request = new CreateDailyLogRequest(habitId, logDate, true, null);

        ResponseEntity<String> response = authPostWithToken("/api/logs", request, String.class, token2);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/logs", null, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // =========================================================================
    // GET /api/logs (getLogs by date range)
    // =========================================================================

    @Test
    void shouldGetLogsInDateRange() {
        LocalDate day1 = LocalDate.now().minusDays(3);
        LocalDate day2 = LocalDate.now().minusDays(1);
        authPost("/api/logs", new CreateDailyLogRequest(habitId, day1, true, null), DailyLogResponse.class);
        authPost("/api/logs", new CreateDailyLogRequest(habitId, day2, true, null), DailyLogResponse.class);

        ResponseEntity<List<DailyLogResponse>> response = getLogs(
                habitId, LocalDate.now().minusDays(5), LocalDate.now());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldReturnEmptyListWhenNoLogsInRange() {
        ResponseEntity<List<DailyLogResponse>> response = getLogs(
                habitId, LocalDate.now().minusDays(7), LocalDate.now().minusDays(1));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void shouldReturn400WhenStartDateAfterEndDate() {
        String url = "/api/logs?habitId=" + habitId
                + "&startDate=2026-02-10&endDate=2026-02-01";
        ResponseEntity<String> response = authGet(url, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // =========================================================================
    // GET /api/logs/{id}
    // =========================================================================

    @Test
    void shouldGetLogById() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        DailyLogResponse created = authPost(
                "/api/logs",
                new CreateDailyLogRequest(habitId, logDate, true, "note"),
                DailyLogResponse.class).getBody();

        ResponseEntity<DailyLogResponse> response = authGet("/api/logs/" + created.id(), DailyLogResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(created.id(), response.getBody().id());
        assertTrue(response.getBody().completed());
    }

    @Test
    void shouldReturn404ForNonExistentLog() {
        ResponseEntity<String> response = authGet("/api/logs/99999", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldNotAllowAccessingAnotherUsersLog() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        DailyLogResponse created = authPost(
                "/api/logs",
                new CreateDailyLogRequest(habitId, logDate, true, null),
                DailyLogResponse.class).getBody();

        String token2 = registerAndGetToken("user2", "user2@example.com", "password123");
        ResponseEntity<String> response = authGetWithToken("/api/logs/" + created.id(), String.class, token2);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // =========================================================================
    // DELETE /api/logs/{id}
    // =========================================================================

    @Test
    void shouldDeleteLogAndReturn204() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        DailyLogResponse created = authPost(
                "/api/logs",
                new CreateDailyLogRequest(habitId, logDate, true, null),
                DailyLogResponse.class).getBody();

        ResponseEntity<Void> response = authDelete("/api/logs/" + created.id());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, authGet("/api/logs/" + created.id(), String.class).getStatusCode());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentLog() {
        ResponseEntity<Void> response = authDelete("/api/logs/99999");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // =========================================================================
    // POST /api/logs/batch
    // =========================================================================

    @Test
    void shouldBatchCreateLogsAndReturn201() {
        LocalDate day1 = LocalDate.now().minusDays(2);
        LocalDate day2 = LocalDate.now().minusDays(1);

        BatchDailyLogRequest request = new BatchDailyLogRequest(List.of(
                new CreateDailyLogRequest(habitId, day1, true, null),
                new CreateDailyLogRequest(habitId, day2, false, "tired")));

        ResponseEntity<List<DailyLogResponse>> response = authPost(
                "/api/logs/batch", request, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldBatchUpsertExistingLogs() {
        LocalDate logDate = LocalDate.now().minusDays(1);
        authPost("/api/logs", new CreateDailyLogRequest(habitId, logDate, true, "original"), DailyLogResponse.class);

        BatchDailyLogRequest batch = new BatchDailyLogRequest(List.of(
                new CreateDailyLogRequest(habitId, logDate, false, "updated")));

        ResponseEntity<List<DailyLogResponse>> response = authPost(
                "/api/logs/batch", batch, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertFalse(response.getBody().get(0).completed());
        assertEquals("updated", response.getBody().get(0).notes());

        // Still only one log for that date
        ResponseEntity<List<DailyLogResponse>> logs = getLogs(habitId, logDate, logDate);
        assertEquals(1, logs.getBody().size());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String registerAndGetToken(String username, String email, String password) {
        RegisterRequest req = new RegisterRequest(username, email, password);
        ResponseEntity<AuthResponse> res = restTemplate.postForEntity("/api/auth/register", req, AuthResponse.class);
        return res.getBody().getAccessToken();
    }

    private HabitResponse createHabit(String name) {
        CreateHabitRequest request = new CreateHabitRequest(name, null, null);
        return authPost("/api/habits", request, HabitResponse.class).getBody();
    }

    private ResponseEntity<List<DailyLogResponse>> getLogs(Long habitId, LocalDate start, LocalDate end) {
        String url = "/api/logs?habitId=" + habitId + "&startDate=" + start + "&endDate=" + end;
        return authGet(url, new ParameterizedTypeReference<>() {});
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

    private <T> ResponseEntity<T> authPost(String url, Object body, ParameterizedTypeReference<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), responseType);
    }

    private <T> ResponseEntity<T> authPostWithToken(String url, Object body, Class<T> responseType, String token) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, authHeadersWithToken(token)), responseType);
    }

    private ResponseEntity<Void> authDelete(String url) {
        return restTemplate.exchange(url, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), Void.class);
    }
}
