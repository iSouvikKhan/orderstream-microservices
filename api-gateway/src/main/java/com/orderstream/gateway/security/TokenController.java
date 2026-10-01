package com.orderstream.gateway.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * DEMO-ONLY token endpoint so the system can be exercised end to end without an identity
 * provider. In production, remove this and point the resource server at Keycloak/Auth0/Cognito.
 */
@RestController
@RequestMapping("/auth")
public class TokenController {

    public record TokenRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,64}") String username) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    private final JwtEncoder encoder;
    private final JwtProperties props;

    public TokenController(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    @PostMapping("/token")
    public TokenResponse issue(@Valid @RequestBody TokenRequest request) {
        Duration ttl = props.ttl() != null ? props.ttl() : Duration.ofHours(1);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(request.username())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("scope", "orders")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
