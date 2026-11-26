package cl.nanitomangas.administracion.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String crear(String id, String email, String role, String typ, Duration ttl) {
        Date ahora = new Date();
        return Jwts.builder().subject(id).claim("email", email).claim("role", role).claim("typ", typ)
                .issuedAt(ahora).expiration(new Date(ahora.getTime() + ttl.toMillis()))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    /** Lanza JwtException si la firma es ajena, el token está alterado o vencido. */
    public Claims verificar(String token) {
        var jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        // El diseño fija HS256; no se aceptan HS384/HS512 aunque la clave sea larga.
        if (!"HS256".equals(jws.getHeader().getAlgorithm())) throw new JwtException("Algoritmo no permitido");
        return jws.getPayload();
    }
}
