package cl.nanitomangas.clientes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Sin cabecera Authorization sigue anónimo (la ruta decide); con token inválido responde 401. */
@Component
public class JwtFilter extends OncePerRequestFilter {
	private final JwtUtil jwt;

	public JwtFilter(JwtUtil jwt) { this.jwt = jwt; }

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String h = req.getHeader("Authorization");
		if (h != null && h.startsWith("Bearer ")) {
			try {
				Claims c = jwt.verificar(h.substring(7));
				var auth = new UsernamePasswordAuthenticationToken(c.getSubject(), null,
						List.of(new SimpleGrantedAuthority("ROLE_" + c.get("role", String.class))));
				SecurityContextHolder.getContext().setAuthentication(auth);
			} catch (JwtException | IllegalArgumentException e) {
				SecurityConfig.escribir(res, 401, "UNAUTHORIZED", "Token inválido o vencido");
				return;
			}
		}
		chain.doFilter(req, res);
	}
}
