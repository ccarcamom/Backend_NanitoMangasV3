package cl.nanitomangas.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Valida el JWT de acceso y aplica los roles por ruta. Reenvia Authorization sin cambios. */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Set<String> STAFF = Set.of("VENDEDOR", "ADMIN");

    private final SecretKey key;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String raw = exchange.getRequest().getURI().getRawPath();
        // Escapes que el gateway y el servicio podrian interpretar distinto: se rechazan.
        if (raw.matches("(?is).*%(2e|2f|5c|3b).*")) {
            return error(exchange, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Ruta no valida");
        }
        // Normaliza una vez: sin parametros ";...", sin "//" repetidas ni barra final.
        String path = raw.replaceAll(";[^/]*", "").replaceAll("/{2,}", "/").replaceAll("(.)/$", "$1");
        if (HttpMethod.OPTIONS.equals(method) || path.equals("/auth") || path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }
        boolean read = HttpMethod.GET.equals(method);
        boolean productos = path.equals("/api/productos") || path.startsWith("/api/productos/");
        boolean mios = path.equals("/api/productos/mios");
        if (productos && read && !mios) {
            return chain.filter(exchange);
        }
        boolean invitado = HttpMethod.POST.equals(method) && path.equals("/api/pedidos");

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null && invitado) {
            return chain.filter(exchange);
        }
        if (header == null || !header.startsWith("Bearer ")) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token ausente");
        }
        String role;
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(header.substring(7)).getPayload();
            if (!"access".equals(claims.get("typ", String.class))) {
                throw new IllegalArgumentException("typ");
            }
            if (claims.getExpiration() == null) {
                throw new IllegalArgumentException("exp");
            }
            role = claims.get("role", String.class);
        } catch (Exception e) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token invalido o vencido");
        }

        boolean allowed;
        if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
            allowed = "ADMIN".equals(role);
        } else if (mios) {
            allowed = "VENDEDOR".equals(role);
        } else if (productos) {
            allowed = role != null && STAFF.contains(role);
        } else {
            allowed = role != null;
        }
        return allowed ? chain.filter(exchange)
                : error(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Rol sin acceso a esta ruta");
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String code, String msg) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"status\":" + status.value() + ",\"error\":\"" + code + "\",\"message\":\"" + msg + "\"}";
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
