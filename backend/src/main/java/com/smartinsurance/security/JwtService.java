package com.smartinsurance.security;

import com.smartinsurance.entity.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    @Value("${app.jwt.secret:}")
    private String secret;
    @Value("${app.jwt.expiration-minutes:60}")
    private long expirationMinutes;

    private SecretKey signingKey() {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(key);
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getUsername()).claim("role", user.getRole().name())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(signingKey()).compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean isValid(String token, UserDetails user) {
        var claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
        if (!claims.getSubject().equalsIgnoreCase(user.getUsername())) return false;
        if (user instanceof AppUser appUser) {
            Number tokenVersion = claims.get("tokenVersion", Number.class);
            return tokenVersion != null && tokenVersion.longValue() == appUser.getTokenVersion();
        }
        return false;
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}
