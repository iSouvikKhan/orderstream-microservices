package com.orderstream.gateway.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 bytes for HS256") String secret,
        @NotBlank String issuer,
        Duration ttl) {

    public SecretKey key() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
