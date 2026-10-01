package com.orderstream.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Nothing listens on port 1, so downstream calls fail and the circuit breaker falls back.
        "ORDER_SERVICE_URL=http://localhost:1",
        "INVENTORY_SERVICE_URL=http://localhost:1",
        "RATE_LIMIT_REPLENISH=1",
        "RATE_LIMIT_BURST=3"})
@Testcontainers
class GatewayIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    WebTestClient client;

    @Test
    void protectedRoutesRequireAJwt() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void forgedTokensAreRejected() {
        client.get().uri("/api/orders")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJldmUifQ.invalidsignature")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unavailableDownstreamTripsToTheFallback() {
        client.mutate().responseTimeout(Duration.ofSeconds(15)).build()
                .get().uri("/api/orders")
                .header("Authorization", "Bearer " + token("henry"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody().jsonPath("$.service").isEqualTo("order-service");
    }

    @Test
    void requestsBeyondTheBurstAreRateLimited() {
        String token = token("ivy");
        List<HttpStatusCode> statuses = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            statuses.add(client.get().uri("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .exchange().returnResult(String.class).getStatus());
        }
        assertThat(statuses).contains(HttpStatus.TOO_MANY_REQUESTS);
    }

    @SuppressWarnings("unchecked")
    private String token(String user) {
        Map<String, Object> body = client.post().uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", user))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).containsKey("accessToken");
        return (String) body.get("accessToken");
    }
}
