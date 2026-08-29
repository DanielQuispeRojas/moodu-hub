package com.idastasoft.licencias.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Service
public class JwtService {

    private static final String DEFAULT_SECRET = "moodu-hub-jwt-secret-key-2026-change-in-production-please";
    private static final int MIN_SECRET_BYTES = 32; // 256 bits

    private final SecretKey key;
    private final long expiracionMs;

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.expiracion-ms:86400000}") long expiracionMs,
            Environment env) {

        // En prod, exigir un JWT_SECRET real (no el default de dev)
        Set<String> activeProfiles = Set.of(env.getActiveProfiles());
        if (activeProfiles.contains("prod")) {
            if (secret == null || secret.isBlank() || secret.equals(DEFAULT_SECRET)) {
                throw new IllegalStateException(
                    "JWT_SECRET no esta configurado o usa el default de desarrollo. " +
                    "En produccion se requiere un secreto seguro (>=256 bits). " +
                    "Setea la variable de entorno JWT_SECRET en Render/Railway."
                );
            }
            if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 256 bits (32 bytes). " +
                    "El valor actual es demasiado corto."
                );
            }
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = expiracionMs;
    }

    public String generarToken(String usuario) {
        return Jwts.builder()
                .subject(usuario)
                .claim("rol", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiracionMs))
                .signWith(key)
                .compact();
    }

    public boolean tokenValido(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
