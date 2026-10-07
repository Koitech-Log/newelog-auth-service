package br.com.newelog.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.newelog.auth.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite e valida JWT assinado com HS256. Os demais serviços podem validar o
 * token com o mesmo segredo (ver README): sub = id, claims email, nome e perfil.
 */
@Service
public class JwtService {

    public static final String EMISSOR = "newelog-auth-service";

    public record Token(String valor, Instant expiraEm) {
    }

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(
            @Value("${app.jwt.secret}") String segredo,
            @Value("${app.jwt.expiration-minutes:480}") long expiracaoMinutos
    ) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
        this.validade = Duration.ofMinutes(expiracaoMinutos);
    }

    public Token gerar(Usuario usuario, Instant agora) {
        Instant expiraEm = agora.plus(validade);
        String valor = Jwts.builder()
                .issuer(EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getEmail())
                .claim("nome", usuario.getNome())
                .claim("perfil", usuario.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave, Jwts.SIG.HS256) // fixo: sem isso o jjwt usaria HS384/HS512 para segredos longos
                .compact();
        return new Token(valor, expiraEm);
    }

    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(EMISSOR)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
