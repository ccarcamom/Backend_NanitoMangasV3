package cl.nanitomangas.clientes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cl.nanitomangas.clientes.repository.MemeRepository;
import cl.nanitomangas.clientes.repository.PedidoRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** VendedoresClient real contra un servidor HTTP real que simula ms-vendedores. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
		"vendedores.read-timeout-ms=300" })
@AutoConfigureMockMvc
class UpstreamHttpTest {
	static final List<String> llamadas = new CopyOnWriteArrayList<>();
	static final HttpServer server = arrancar();

	static HttpServer arrancar() {
		try {
			HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			s.createContext("/api/productos/", ex -> {
				llamadas.add(ex.getRequestURI().getPath());
				boolean existe = ex.getRequestURI().getPath().endsWith("/lento");
				String j = existe ? "{\"id\":\"lento\",\"name\":\"L\",\"price\":1000,\"stock\":9,\"sellerId\":\"v1\"}"
						: "{\"status\":404,\"error\":\"NOT_FOUND\",\"message\":\"no existe\"}";
				responder(ex, existe ? 200 : 404, j);
			});
			s.createContext("/internal/stock/", ex -> {
				llamadas.add(ex.getRequestURI().getPath());
				try { Thread.sleep(1500); } catch (InterruptedException e) { /* ignorar */ }
				responder(ex, 200, "{}");
			});
			s.start();
			return s;
		} catch (IOException e) { throw new IllegalStateException(e); }
	}

	static void responder(com.sun.net.httpserver.HttpExchange ex, int code, String json) throws IOException {
		byte[] b = json.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		try { ex.sendResponseHeaders(code, b.length); ex.getResponseBody().write(b); } catch (IOException e) { /* cliente cortó */ }
		ex.close();
	}

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("vendedores.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterAll
	static void parar() { server.stop(0); }

	@Autowired MockMvc mvc;
	@MockitoBean PedidoRepository pedidos;
	@MockitoBean MemeRepository memes;

	static String body(String id) {
		return "{\"buyer\":{\"name\":\"Ana\",\"rut\":\"1-9\"},\"email\":\"a@x.cl\",\"delivery\":{\"method\":\"retiro\"},"
				+ "\"items\":[{\"productId\":\"" + id + "\",\"qty\":1}]}";
	}

	@Test
	void vendedores_404_con_cuerpo_de_error_da_409_PRODUCT_NOT_FOUND() throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body("fantasma")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND:fantasma"));
	}

	@Test
	void timeout_en_descontar_da_502_UPSTREAM_TIMEOUT_y_no_restaura() throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body("lento")))
				.andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").value("UPSTREAM_TIMEOUT"));
		assertTrue(llamadas.contains("/internal/stock/descontar"));
		assertTrue(!llamadas.contains("/internal/stock/restaurar"));
	}
}
