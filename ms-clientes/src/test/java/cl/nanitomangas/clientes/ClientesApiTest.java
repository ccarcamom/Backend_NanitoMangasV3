package cl.nanitomangas.clientes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cl.nanitomangas.clientes.client.VendedoresClient;
import cl.nanitomangas.clientes.client.VendedoresClient.Producto;
import cl.nanitomangas.clientes.model.Meme;
import cl.nanitomangas.clientes.model.Pedido;
import cl.nanitomangas.clientes.repository.MemeRepository;
import cl.nanitomangas.clientes.repository.PedidoRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
		"jwt.secret=test-secret-test-secret-test-secret-123" })
@AutoConfigureMockMvc
class ClientesApiTest {
	static final String SECRET = "test-secret-test-secret-test-secret-123";
	@Autowired MockMvc mvc;
	@MockitoBean PedidoRepository pedidos;
	@MockitoBean MemeRepository memes;
	@MockitoBean VendedoresClient vendedores;

	static String token(String sub, String role) { return token(sub, role, "access", SECRET, 600_000); }

	static String token(String sub, String role, String typ, String secret, long ttl) {
		return Jwts.builder().subject(sub).claim("email", sub + "@x.cl").claim("role", role).claim("typ", typ)
				.expiration(new Date(System.currentTimeMillis() + ttl))
				.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
	}

	static String bearer(String sub, String role) { return "Bearer " + token(sub, role); }

	@BeforeEach
	void setUp() {
		when(vendedores.obtener("p1")).thenReturn(Optional.of(new Producto("p1", "Berserk 1", 10000, null, 5, "v1")));
		when(vendedores.obtener("p2")).thenReturn(Optional.of(new Producto("p2", "Oferta", 30000, 20000L, 5, "v2")));
		when(pedidos.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
		when(pedidos.insert(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
		when(memes.save(any(Meme.class))).thenAnswer(i -> i.getArgument(0));
	}

	static final String ADDR = "{\"street\":\"Calle 1\",\"region\":\"RM\",\"comuna\":\"Santiago\"}";
	static final String ITEMS = "[{\"productId\":\"p1\",\"qty\":1}]";

	static String raw(String delivery) {
		return "{\"buyer\":{\"name\":\"Ana\",\"rut\":\"1-9\"},\"email\":\"a@x.cl\",\"delivery\":" + delivery + ",\"items\":" + ITEMS + "}";
	}

	void espera400(String json) throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
		verifyNoInteractions(vendedores);
	}

	@Test
	void method_nulo_o_desconocido_da_400() throws Exception {
		espera400(raw("{\"address\":" + ADDR + "}"));
		espera400(raw("{\"method\":\"dron\",\"address\":" + ADDR + "}"));
	}

	@Test
	void despacho_sin_direccion_completa_da_400_y_retiro_sin_direccion_pasa() throws Exception {
		espera400(raw("{\"method\":\"despacho\"}"));
		espera400(raw("{\"method\":\"despacho\",\"address\":{\"street\":\"C\",\"region\":\" \",\"comuna\":\"S\"}}"));
		espera400(raw("{\"method\":\"despacho\",\"address\":{\"street\":\"C\",\"region\":\"R\"}}"));
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(raw("{\"method\":\"retiro\"}")))
				.andExpect(status().isCreated());
	}

	@Test
	void street_de_mas_de_120_o_comuna_vacia_da_400() throws Exception {
		espera400(raw("{\"method\":\"despacho\",\"address\":{\"street\":\"" + "c".repeat(121) + "\",\"region\":\"R\",\"comuna\":\"S\"}}"));
		espera400(raw("{\"method\":\"despacho\",\"address\":{\"street\":\"C\",\"region\":\"R\",\"comuna\":\"\"}}"));
	}

	@Test
	void api_docs_declara_bearerAuth() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
				.andExpect(jsonPath("$.security[0].bearerAuth").exists())
				.andExpect(jsonPath("$.paths['/api/pedidos'].post.security").isEmpty());
	}

	@Test
	void email_invalido_o_nombre_largo_da_400() throws Exception {
		espera400(raw("{\"method\":\"retiro\"}").replace("a@x.cl", "no-es-email"));
		espera400(raw("{\"method\":\"retiro\"}").replace("\"Ana\"", "\"" + "a".repeat(101) + "\""));
	}

	@Test
	void rutas_inexistentes_y_metodos_no_permitidos_respetan_404_y_405() throws Exception {
		mvc.perform(get("/api/nada").header("Authorization", bearer("u1", "CLIENTE"))).andExpect(status().isNotFound());
		mvc.perform(delete("/api/pedidos").header("Authorization", bearer("a1", "ADMIN"))).andExpect(status().isMethodNotAllowed());
	}

	static String body(String method, String items) {
		return "{\"buyer\":{\"name\":\"Ana\",\"rut\":\"1-9\"},\"email\":\"a@x.cl\","
				+ "\"delivery\":{\"method\":\"" + method + "\",\"address\":" + ADDR + "},\"items\":" + items + "}";
	}

	@Test
	void pedido_calcula_subtotal_envio_y_total_en_el_servidor() throws Exception {
		// subtotal 10000 (+3990); salePrice 20000*2 = 40000 -> gratis; retiro 0
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("despacho", "[{\"productId\":\"p1\",\"qty\":1}]")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.subtotal").value(10000))
				.andExpect(jsonPath("$.shipping").value(3990)).andExpect(jsonPath("$.total").value(13990))
				.andExpect(jsonPath("$.status").value("pagado")).andExpect(jsonPath("$.items[0].unitPrice").value(10000))
				.andExpect(jsonPath("$.items[0].name").value("Berserk 1")).andExpect(jsonPath("$.items[0].sellerId").value("v1"));
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("despacho", "[{\"productId\":\"p2\",\"qty\":2}]")))
				.andExpect(jsonPath("$.subtotal").value(40000)).andExpect(jsonPath("$.shipping").value(0))
				.andExpect(jsonPath("$.total").value(40000)).andExpect(jsonPath("$.items[0].unitPrice").value(20000));
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":1}]")))
				.andExpect(jsonPath("$.shipping").value(0)).andExpect(jsonPath("$.total").value(10000));
		verify(vendedores, times(3)).descontar(anyList());
	}

	@Test
	void pedido_de_invitado_guarda_userId_nulo() throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":1}]")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.userId").doesNotExist());
	}

	@Test
	void pedido_con_token_guarda_userId() throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer("u7", "CLIENTE"))
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":1}]")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value("u7"));
	}

	@Test
	void token_invalido_vencido_refresh_o_de_otro_secreto_da_401() throws Exception {
		String json = body("retiro", "[{\"productId\":\"p1\",\"qty\":1}]");
		for (String t : List.of("basura", token("u", "CLIENTE", "access", "otro-secreto-otro-secreto-otro-secreto-1", 60_000),
				token("u", "CLIENTE", "access", SECRET, -60_000), token("u", "CLIENTE", "refresh", SECRET, 60_000)))
			mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + t)
					.content(json)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/pedidos/mios")).andExpect(status().isUnauthorized());
		verifyNoInteractions(vendedores);
	}

	@Test
	void ids_correlativos_NM_000001_NM_000002() throws Exception {
		String items = "[{\"productId\":\"p1\",\"qty\":1}]";
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body("retiro", items)))
				.andExpect(jsonPath("$.id").value("NM-000001"));
		when(pedidos.findFirstByOrderByIdDesc()).thenReturn(Optional.of(pedido("NM-000001", "u1", "v1", 1)));
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body("retiro", items)))
				.andExpect(jsonPath("$.id").value("NM-000002"));
	}

	@Test
	void pedido_sin_items_da_400() throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body("retiro", "[]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
		verifyNoInteractions(vendedores);
	}

	@Test
	void pedido_con_qty_0_o_negativa_da_400() throws Exception {
		for (int q : new int[] { 0, -2 })
			mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
					.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":" + q + "}]"))).andExpect(status().isBadRequest());
		verify(vendedores, never()).descontar(anyList());
	}

	@Test
	void producto_inexistente_da_409_y_no_descuenta() throws Exception {
		when(vendedores.obtener("nope")).thenReturn(Optional.empty());
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":1},{\"productId\":\"nope\",\"qty\":1}]")))
				.andExpect(status().isConflict());
		verify(vendedores, never()).descontar(anyList());
	}

	@Test
	void sin_stock_da_409_OUT_OF_STOCK_y_no_guarda() throws Exception {
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":6}]")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OUT_OF_STOCK:p1"));
		doThrow(new VendedoresClient.SinStockException("p1")).when(vendedores).descontar(anyList());
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":1}]")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OUT_OF_STOCK:p1"));
		verify(pedidos, never()).insert(any(Pedido.class));
		verify(vendedores, never()).restaurar(anyList());
	}

	@Test
	void si_guardar_falla_se_llama_restaurar() throws Exception {
		when(pedidos.insert(any(Pedido.class))).thenThrow(new RuntimeException("mongo caído"));
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":2}]"))).andExpect(status().is5xxServerError());
		verify(vendedores).descontar(anyList());
		verify(vendedores).restaurar(List.of(new VendedoresClient.StockItem("p1", 2)));
	}

	@Test
	void id_duplicado_reintenta_una_vez() throws Exception {
		when(pedidos.insert(any(Pedido.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("dup"))
				.thenAnswer(i -> i.getArgument(0));
		mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
				.content(body("retiro", "[{\"productId\":\"p1\",\"qty\":1}]"))).andExpect(status().isCreated());
		verify(vendedores, never()).restaurar(anyList());
	}

	static Pedido pedido(String id, String userId, String sellerId, int dia) {
		return new Pedido(id, userId, "a@x.cl", new Pedido.Buyer("Ana", "1-9"), new Pedido.Delivery("retiro", null),
				List.of(new Pedido.Item("p1", "Berserk 1", 10000, 1, sellerId),
						new Pedido.Item("p9", "Otro", 500, 1, "otroVendedor")),
				10500, 0, 10500, Instant.parse("2026-01-0" + dia + "T00:00:00Z"), "pagado");
	}

	@Test
	void mios_devuelve_solo_los_del_token_mas_nuevos_primero() throws Exception {
		when(pedidos.findByUserIdOrderByCreatedAtDesc("u1"))
				.thenReturn(List.of(pedido("NM-000003", "u1", "v1", 3), pedido("NM-000001", "u1", "v1", 1)));
		mvc.perform(get("/api/pedidos/mios").header("Authorization", bearer("u1", "VENDEDOR")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value("NM-000003"));
		verify(pedidos).findByUserIdOrderByCreatedAtDesc("u1");
		verify(pedidos, never()).findAllByOrderByCreatedAtDesc();
	}

	@Test
	void vendedor_ve_solo_pedidos_con_sus_productos() throws Exception {
		when(pedidos.findByItemsSellerIdOrderByCreatedAtDesc("v1")).thenReturn(List.of(pedido("NM-000001", "u1", "v1", 1)));
		mvc.perform(get("/api/pedidos/vendedor").header("Authorization", bearer("v1", "VENDEDOR")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].items.length()").value(1)).andExpect(jsonPath("$[0].items[0].sellerId").value("v1"));
		mvc.perform(get("/api/pedidos/vendedor").header("Authorization", bearer("u1", "CLIENTE")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/pedidos").header("Authorization", bearer("v1", "VENDEDOR"))).andExpect(status().isForbidden());
	}

	@Test
	void admin_ve_todos() throws Exception {
		when(pedidos.findAllByOrderByCreatedAtDesc())
				.thenReturn(List.of(pedido("NM-000002", "u2", "v1", 2), pedido("NM-000001", "u1", "v1", 1)));
		mvc.perform(get("/api/pedidos").header("Authorization", bearer("a1", "ADMIN")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
		when(pedidos.findById("NM-000001")).thenReturn(Optional.of(pedido("NM-000001", "u1", "v1", 1)));
		mvc.perform(get("/api/pedidos/NM-000001").header("Authorization", bearer("a1", "ADMIN"))).andExpect(status().isOk());
		mvc.perform(get("/api/pedidos/NM-000001").header("Authorization", bearer("u1", "CLIENTE"))).andExpect(status().isOk());
	}

	@Test
	void cliente_no_ve_pedido_ajeno_403() throws Exception {
		when(pedidos.findById("NM-000001")).thenReturn(Optional.of(pedido("NM-000001", "u1", "v1", 1)));
		mvc.perform(get("/api/pedidos/NM-000001").header("Authorization", bearer("u2", "CLIENTE")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/pedidos").header("Authorization", bearer("u2", "CLIENTE"))).andExpect(status().isForbidden());
	}

	// --- memes ---
	static final String MEME = "{\"productId\":\"p1\",\"language\":\"es\",\"pageIndex\":2,\"bubbles\":[{\"x\":0.1,\"y\":0.2,"
			+ "\"w\":0.3,\"h\":0.4,\"text\":\"hola\",\"fontFamily\":\"Inter\",\"fontSize\":20,\"color\":\"#000\",\"background\":\"#fff\"}]}";

	static Meme meme(String id, String owner) {
		return new Meme(id, owner, "p1", "es", 2, List.of(), Instant.now());
	}

	@Test
	void memes_rol_VENDEDOR_da_403() throws Exception {
		mvc.perform(get("/api/memes").header("Authorization", bearer("v1", "VENDEDOR"))).andExpect(status().isForbidden());
		mvc.perform(post("/api/memes").header("Authorization", bearer("v1", "VENDEDOR")).contentType(MediaType.APPLICATION_JSON)
				.content(MEME)).andExpect(status().isForbidden());
	}

	@Test
	void cliente_crea_lista_edita_y_borra_su_meme() throws Exception {
		String c = bearer("u1", "CLIENTE");
		mvc.perform(post("/api/memes").header("Authorization", c).contentType(MediaType.APPLICATION_JSON).content(MEME))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value("u1"))
				.andExpect(jsonPath("$.bubbles[0].text").value("hola"));
		when(memes.findByOwnerIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(meme("m1", "u1")));
		mvc.perform(get("/api/memes").header("Authorization", c)).andExpect(jsonPath("$.length()").value(1));
		when(memes.findById("m1")).thenReturn(Optional.of(meme("m1", "u1")));
		mvc.perform(put("/api/memes/m1").header("Authorization", c).contentType(MediaType.APPLICATION_JSON).content(MEME))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value("m1")).andExpect(jsonPath("$.ownerId").value("u1"));
		mvc.perform(delete("/api/memes/m1").header("Authorization", c)).andExpect(status().isNoContent());
		verify(memes).delete(any(Meme.class));
	}

	@Test
	void cliente_no_edita_meme_ajeno_403() throws Exception {
		when(memes.findById("m1")).thenReturn(Optional.of(meme("m1", "u1")));
		String otro = bearer("u2", "CLIENTE");
		mvc.perform(put("/api/memes/m1").header("Authorization", otro).contentType(MediaType.APPLICATION_JSON).content(MEME))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/api/memes/m1").header("Authorization", otro)).andExpect(status().isForbidden());
		mvc.perform(get("/api/memes/m1").header("Authorization", otro)).andExpect(status().isForbidden());
		verify(memes, never()).save(any(Meme.class));
	}

	@Test
	void admin_borra_cualquier_meme() throws Exception {
		when(memes.findById("m1")).thenReturn(Optional.of(meme("m1", "u1")));
		mvc.perform(delete("/api/memes/m1").header("Authorization", bearer("a1", "ADMIN"))).andExpect(status().isNoContent());
		verify(memes).delete(any(Meme.class));
		mvc.perform(put("/api/memes/m1").header("Authorization", bearer("a1", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
				.content(MEME)).andExpect(status().isForbidden());
	}

	@Test
	void meme_con_bubbles_fuera_de_0_a_1_da_400() throws Exception {
		for (String malo : List.of(MEME.replace("\"x\":0.1", "\"x\":1.5"), MEME.replace("\"h\":0.4", "\"h\":-0.1")))
			mvc.perform(post("/api/memes").header("Authorization", bearer("u1", "CLIENTE")).contentType(MediaType.APPLICATION_JSON)
					.content(malo)).andExpect(status().isBadRequest());
		verify(memes, never()).save(any(Meme.class));
	}
}
