package dev.portfolio.expenses;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseApiTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ExpenseRepository repository;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String BASE = "/api/v1/expenses";

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsReadsUpdatesAndDeletesAnExpense() throws Exception {
        var created = request("POST", BASE, expense("Lunch", "12.30", "FOOD", "2026-09-01"));
        assertThat(created.statusCode()).isEqualTo(201);
        var location = created.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(BASE + "/");
        UUID.fromString(body(created).get("id").asText());

        var read = request("GET", location, null);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(body(read).get("amount").decimalValue()).isEqualByComparingTo("12.30");
        assertThat(body(read).get("currency").asText()).isEqualTo("EUR");

        var updated = request("PUT", location, expense("  Train  ", "24.50", "TRANSPORT", "2026-09-02"));
        assertThat(updated.statusCode()).isEqualTo(200);
        var persisted = body(request("GET", location, null));
        assertThat(persisted.get("description").asText()).isEqualTo("Train");
        assertThat(persisted.get("category").asText()).isEqualTo("TRANSPORT");
        assertThat(persisted.get("amount").decimalValue()).isEqualByComparingTo("24.50");
        assertThat(persisted.get("incurredOn").asText()).isEqualTo("2026-09-02");

        assertThat(request("DELETE", location, null).statusCode()).isEqualTo(204);
        var missing = request("GET", location, null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(body(missing).get("detail").asText()).isEqualTo("Expense not found");
    }

    @Test
    void filtersInclusiveDatesAndCategoryWithStablePagination() throws Exception {
        request("POST", BASE, expense("Old food", "1.00", "FOOD", "2026-08-31"));
        request("POST", BASE, expense("Start", "2.00", "FOOD", "2026-09-01"));
        request("POST", BASE, expense("End A", "3.00", "FOOD", "2026-09-08"));
        request("POST", BASE, expense("End B", "4.00", "FOOD", "2026-09-08"));
        request("POST", BASE, expense("Later food", "5.00", "FOOD", "2026-09-09"));
        request("POST", BASE, expense("Bus", "6.00", "TRANSPORT", "2026-09-08"));
        String query = BASE + "?category=FOOD&from=2026-09-01&to=2026-09-08&size=2";
        var firstResponse = request("GET", query, null);
        assertThat(firstResponse.statusCode()).isEqualTo(200);
        var first = body(firstResponse);
        var second = body(request("GET", query + "&page=1", null));
        assertThat(first.get("totalElements").asInt()).isEqualTo(3);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("content").size()).isEqualTo(2);
        assertThat(first.get("content").get(0).get("incurredOn").asText()).isEqualTo("2026-09-08");
        assertThat(first.get("content").get(1).get("incurredOn").asText()).isEqualTo("2026-09-08");
        assertThat(second.get("content").size()).isEqualTo(1);
        assertThat(second.get("content").get(0).get("description").asText()).isEqualTo("Start");
        assertThat(body(request("GET", query, null))).isEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "10000000000.00"})
    void rejectsInvalidAmountsWithoutWriting(String amount) throws Exception {
        var response = request("POST", BASE, expense("Lunch", amount, "FOOD", "2026-09-01"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).get("errors").has("amount")).isTrue();
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"description\":\" \"}",
            "{\"description\":\"Lunch\",\"amount\":1,\"category\":\"UNKNOWN\",\"incurredOn\":\"2026-09-01\"}",
            "{\"description\":\"Lunch\",\"amount\":1,\"category\":\"FOOD\",\"incurredOn\":\"bad-date\"}"})
    void rejectsInvalidBodies(String payload) throws Exception {
        var response = request("POST", BASE, payload);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).get("status").asInt()).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?size=0", "?size=101", "?page=-1", "?category=INVALID",
            "?from=invalid", "?from=2026-09-08&to=2026-09-01", "/not-a-uuid"})
    void rejectsInvalidQueryParametersAndIds(String suffix) throws Exception {
        var response = request("GET", BASE + suffix, null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).get("status").asInt()).isEqualTo(400);
    }

    @Test
    void invalidUpdateLeavesTheStoredExpenseUnchanged() throws Exception {
        var created = request("POST", BASE, expense("Lunch", "12.30", "FOOD", "2026-09-01"));
        var location = created.headers().firstValue("Location").orElseThrow();
        assertThat(request("PUT", location, expense("Lunch", "-1", "FOOD", "2026-09-01")).statusCode()).isEqualTo(400);
        assertThat(body(request("GET", location, null)).get("amount").decimalValue()).isEqualByComparingTo("12.30");
    }

    @Test
    void returnsNotFoundForMissingUpdateAndDelete() throws Exception {
        var path = BASE + "/" + UUID.randomUUID();
        assertThat(request("PUT", path, expense("Lunch", "1.00", "FOOD", "2026-09-01")).statusCode()).isEqualTo(404);
        assertThat(request("DELETE", path, null).statusCode()).isEqualTo(404);
        assertThat(repository.count()).isZero();
    }

    @Test
    void returnsAnEmptyPageAndHealthyDatabase() throws Exception {
        var page = body(request("GET", BASE, null));
        assertThat(page.get("content").size()).isZero();
        assertThat(page.get("totalElements").asLong()).isZero();
        var health = request("GET", "/actuator/health", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(body(health).get("status").asText()).isEqualTo("UP");
    }

    private String expense(String description, String amount, String category, String date) {
        return """
                {"description":"%s","amount":%s,"category":"%s","incurredOn":"%s"}
                """.formatted(description, amount, category, date);
    }

    private HttpResponse<String> request(String method, String path, String payload) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }
}
