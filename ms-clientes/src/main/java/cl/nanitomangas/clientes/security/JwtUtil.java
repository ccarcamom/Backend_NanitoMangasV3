package cl.nanitomangas.clientes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
	private final SecretKey key;

	public JwtUtil(@Value("${jwt.secret}") String secret) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	/** Verifica firma, vencimiento y typ=access. Lanza JwtException si algo falla. */
	public Claims verificar(String token) {
		Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		if (!"access".equals(c.get("typ", String.class))) throw new JwtException("typ inválido");
		return c;
	}
}
