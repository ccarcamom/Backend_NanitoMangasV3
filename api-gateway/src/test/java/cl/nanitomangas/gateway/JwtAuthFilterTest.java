package cl.nanitomangas.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=test-secret-test-secret-test-secret-123456")
class JwtAuthFilterTest {

    static final String SECRET = "test-secret-test-secret-test-secret-123456";
    static final MockWebServer downstream = new MockWebServer();
    static {
        downstream.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                return new MockResponse().setBody("[]").addHeader("Content-Type", "application/json");
            }
        });
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry r) {
        String url = downstream.url("/").toString();
        r.add("ADMIN_URL", () -> url);
        r.add("VENDEDORES_URL", () -> url);
        r.add("CLIENTES_URL", () -> url);
    }

    @AfterAll
    static void stop() throws Exception {
        downstream.shutdown();
    }

    @Autowired
    WebTestClient web;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    static String token(String role, String typ, long ttlMs, String secret) {
        return Jwts.builder().subject("u1").claim("email", "a@b.cl").claim("role", role).claim("typ", typ)
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    static String access(String role) {
        return "Bearer " + token(role, "access", 60_000, SECRET);
    }

    void status(HttpMethod m, String uri, String auth, HttpStatus expected) {
        WebTestClient.RequestHeadersSpec<?> spec = web.method(m).uri(java.net.URI.create("http://localhost:" + port + uri));
        if (auth != null) spec = spec.header("Authorization", auth);
        spec.exchange().expectStatus().isEqualTo(expected);
    }

    @Test
    void sin_token_en_ruta_protegida_401() {
        status(HttpMethod.GET, "/api/admin/usuarios", null, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void token_vencido_401() {
        status(HttpMethod.GET, "/api/pedidos/mios", "Bearer " + token("CLIENTE", "access", -60_000, SECRET), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void token_con_otro_secreto_o_refresh_401() {
        status(HttpMethod.GET, "/api/pedidos/mios", "Bearer " + token("ADMIN", "access", 60_000, "otro-secreto-otro-secreto-otro-secreto-1"), HttpStatus.UNAUTHORIZED);
        status(HttpMethod.GET, "/api/pedidos/mios", "Bearer " + token("CLIENTE", "refresh", 60_000, SECRET), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rol_CLIENTE_en_api_admin_403() {
        status(HttpMethod.GET, "/api/admin/usuarios", access("CLIENTE"), HttpStatus.FORBIDDEN);
    }

    @Test
    void ADMIN_en_api_admin_pasa_con_authorization_intacta() throws Exception {
        String auth = access("ADMIN");
        status(HttpMethod.GET, "/api/admin/usuarios", auth, HttpStatus.OK);
        RecordedRequest last = null;
        for (RecordedRequest r; (r = downstream.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS)) != null; ) last = r;
        assertEquals(auth, last.getHeader("Authorization"));
    }

    @Test
    void GET_productos_publico_sin_token() {
        status(HttpMethod.GET, "/api/productos", null, HttpStatus.OK);
    }

    @Test
    void POST_productos_con_rol_CLIENTE_403() {
        status(HttpMethod.POST, "/api/productos", access("CLIENTE"), HttpStatus.FORBIDDEN);
        status(HttpMethod.POST, "/api/productos", access("VENDEDOR"), HttpStatus.OK);
    }

    @Test
    void productos_mios_exige_vendedor() {
        status(HttpMethod.GET, "/api/productos/mios", null, HttpStatus.UNAUTHORIZED);
        status(HttpMethod.GET, "/api/productos/mios", access("CLIENTE"), HttpStatus.FORBIDDEN);
        status(HttpMethod.GET, "/api/productos/mios", access("ADMIN"), HttpStatus.FORBIDDEN);
        status(HttpMethod.GET, "/api/productos/mios", access("VENDEDOR"), HttpStatus.OK);
    }

    @Test
    void POST_pedidos_sin_token_pasa_como_invitado() {
        status(HttpMethod.POST, "/api/pedidos", null, HttpStatus.OK);
        status(HttpMethod.GET, "/api/pedidos/mios", null, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void auth_login_publico() {
        status(HttpMethod.POST, "/auth/login", null, HttpStatus.OK);
    }

    @Test
    void internal_stock_no_se_enruta_404() {
        status(HttpMethod.POST, "/internal/stock/descontar", null, HttpStatus.NOT_FOUND);
        status(HttpMethod.POST, "/internal/stock/descontar", access("ADMIN"), HttpStatus.NOT_FOUND);
    }

    @Test
    void cors_solo_localhost_5173() {
        web.options().uri("/api/productos").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
        web.options().uri("/api/productos").header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void POST_pedidos_con_token_invalido_401() {
        status(HttpMethod.POST, "/api/pedidos", "Bearer basura", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void api_admin_sin_barra_y_con_barra_final_aplican_regla_admin() {
        status(HttpMethod.GET, "/api/admin", access("CLIENTE"), HttpStatus.FORBIDDEN);
        status(HttpMethod.GET, "/api/admin/", access("CLIENTE"), HttpStatus.FORBIDDEN);
        status(HttpMethod.GET, "/api/admin//usuarios", access("CLIENTE"), HttpStatus.FORBIDDEN);
    }

    @Test
    void productos_mios_con_barra_final_exige_vendedor() {
        status(HttpMethod.GET, "/api/productos/mios/", null, HttpStatus.UNAUTHORIZED);
        status(HttpMethod.GET, "/api/productos/mios/", access("CLIENTE"), HttpStatus.FORBIDDEN);
    }

    @Test
    void token_sin_exp_401() {
        String t = Jwts.builder().subject("u1").claim("role", "ADMIN").claim("typ", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        status(HttpMethod.GET, "/api/admin/usuarios", "Bearer " + t, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void PUT_y_DELETE_productos_con_rol_CLIENTE_403() {
        status(HttpMethod.PUT, "/api/productos/1", access("CLIENTE"), HttpStatus.FORBIDDEN);
        status(HttpMethod.DELETE, "/api/productos/1", access("CLIENTE"), HttpStatus.FORBIDDEN);
    }

    @Test
    void parametros_de_ruta_con_punto_y_coma_no_esquivan_reglas() {
        status(HttpMethod.GET, "/api/productos/mios;x=1", access("CLIENTE"), HttpStatus.FORBIDDEN);
        status(HttpMethod.GET, "/api/productos/mios;x=1", null, HttpStatus.UNAUTHORIZED);
        status(HttpMethod.GET, "/api/admin;x=1/usuarios", access("CLIENTE"), HttpStatus.FORBIDDEN);
    }

    @Test
    void ruta_con_escapes_ambiguos_400() {
        status(HttpMethod.GET, "/api/admin/%2e%2e/auth/login", null, HttpStatus.BAD_REQUEST);
        status(HttpMethod.GET, "/api/productos/%2fmios", null, HttpStatus.BAD_REQUEST);
        status(HttpMethod.GET, "/api/productos/%5Cmios", null, HttpStatus.BAD_REQUEST);
        status(HttpMethod.GET, "/api/productos/mios%3Bx", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    void ruta_normal_con_query_sigue_funcionando() {
        status(HttpMethod.GET, "/api/productos?q=one%20piece&genre=shonen", null, HttpStatus.OK);
    }
}
