package cl.nanitomangas.vendedores;

import cl.nanitomangas.vendedores.model.Producto;
import cl.nanitomangas.vendedores.repository.ProductoRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
        "jwt.secret=" + ProductoApiTest.SECRET,
        "internal.key=clave-interna"})
@AutoConfigureMockMvc
class ProductoApiTest {

    static final String SECRET = "test-secret-key-test-secret-key-0123456789";
    static final String INTERNAL = "[{\"productId\":\"a\",\"qty\":2}]";

    @Autowired MockMvc mvc;
    @MockitoBean ProductoRepository repo;
    @MockitoBean MongoTemplate template;

    static String token(String sub, String role, String typ, String secret, long ttlMs) {
        return "Bearer " + Jwts.builder().subject(sub).claim("email", sub + "@x.cl").claim("role", role)
                .claim("typ", typ).expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    static String vendedor(String id) { return token(id, "VENDEDOR", "access", SECRET, 60_000); }

    static Producto producto(String id, String sellerId) {
        Producto p = new Producto();
        p.id = id; p.name = "Prod " + id; p.price = 1000; p.stock = 5; p.sellerId = sellerId;
        return p;
    }

    @Test
    void lista_publica_sin_token() throws Exception {
        when(template.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(producto("a", "v1")));
        mvc.perform(get("/api/productos")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("a"));
        mvc.perform(get("/api/productos/mios")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtra_por_categoryId_genre_y_q() throws Exception {
        when(template.find(any(Query.class), eq(Producto.class))).thenReturn(List.of());
        mvc.perform(get("/api/productos?categoryId=mangas&genre=shonen&q=piece")).andExpect(status().isOk());
        ArgumentCaptor<Query> cap = ArgumentCaptor.forClass(Query.class);
        verify(template).find(cap.capture(), eq(Producto.class));
        Document q = cap.getValue().getQueryObject();
        assertEquals("mangas", q.get("categoryId"));
        assertEquals("shonen", q.get("genre"));
        assertTrue(q.containsKey("name"));
    }

    @Test
    void crear_como_vendedor_asigna_sellerId_del_token() throws Exception {
        when(repo.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/api/productos").header("Authorization", vendedor("v1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"price\":1000,\"stock\":3,\"sellerId\":\"otro\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sellerId").value("v1"));
    }

    @Test
    void cliente_no_crea_403() throws Exception {
        mvc.perform(post("/api/productos").header("Authorization", token("c1", "CLIENTE", "access", SECRET, 60_000))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"N\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isForbidden());
        verify(repo, never()).save(any());
    }

    @Test
    void token_alterado_vencido_o_de_otro_secreto_da_401() throws Exception {
        String body = "{\"name\":\"N\",\"price\":1,\"stock\":1}";
        for (String t : List.of(
                token("v1", "VENDEDOR", "access", "otro-secreto-otro-secreto-otro-secreto-123", 60_000),
                token("v1", "VENDEDOR", "access", SECRET, -60_000),
                token("v1", "VENDEDOR", "refresh", SECRET, 60_000),
                vendedor("v1").replace('.', ',').substring(0, 30))) {
            mvc.perform(post("/api/productos").header("Authorization", t)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        }
        // claim role alterado: se rompe la firma
        String[] partes = vendedor("v1").substring(7).split("\\.");
        String admin = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = new String(java.util.Base64.getUrlDecoder().decode(partes[1])).replace("VENDEDOR", "ADMIN");
        String forjado = "Bearer " + admin + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes()) + "." + partes[2];
        mvc.perform(post("/api/productos").header("Authorization", forjado)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void vendedor_no_edita_producto_ajeno_403() throws Exception {
        when(repo.findById("a")).thenReturn(Optional.of(producto("a", "v2")));
        mvc.perform(put("/api/productos/a").header("Authorization", vendedor("v1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/productos/a").header("Authorization", vendedor("v1"))).andExpect(status().isForbidden());
        verify(repo, never()).save(any());
        verify(repo, never()).deleteById(any());
    }

    @Test
    void admin_edita_cualquiera() throws Exception {
        when(repo.findById("a")).thenReturn(Optional.of(producto("a", "v2")));
        when(repo.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        mvc.perform(put("/api/productos/a").header("Authorization", token("ad", "ADMIN", "access", SECRET, 60_000))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("X"))
                .andExpect(jsonPath("$.sellerId").value("v2"));
    }

    @Test
    void internal_sin_clave_da_403() throws Exception {
        mvc.perform(post("/internal/stock/descontar").contentType(MediaType.APPLICATION_JSON).content(INTERNAL))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/stock/restaurar").header("X-Internal-Key", "mala")
                .contentType(MediaType.APPLICATION_JSON).content(INTERNAL)).andExpect(status().isForbidden());
        verifyNoInteractions(template);
    }

    @Test
    void descontar_resta_stock() throws Exception {
        when(template.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(producto("a", "v1"));
        mvc.perform(post("/internal/stock/descontar").header("X-Internal-Key", "clave-interna")
                .contentType(MediaType.APPLICATION_JSON).content(INTERNAL)).andExpect(status().isNoContent());
        ArgumentCaptor<UpdateDefinition> up = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(template).findAndModify(any(Query.class), up.capture(), eq(Producto.class));
        assertEquals(-2, ((Document) up.getValue().getUpdateObject().get("$inc")).get("stock"));
    }

    @Test
    void descontar_con_stock_insuficiente_da_409_y_no_cambia_nada() throws Exception {
        // el primero se descuenta, el segundo falla: el primero se devuelve
        when(template.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(producto("a", "v1")).thenReturn(null);
        mvc.perform(post("/internal/stock/descontar").header("X-Internal-Key", "clave-interna")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\":\"a\",\"qty\":2},{\"productId\":\"b\",\"qty\":1}]"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("OUT_OF_STOCK:b"));
        ArgumentCaptor<UpdateDefinition> up = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(template).updateFirst(any(Query.class), up.capture(), eq(Producto.class));
        assertEquals(2, ((Document) up.getValue().getUpdateObject().get("$inc")).get("stock"));
    }

    @Test
    void descontar_con_items_vacios_o_qty_invalida_da_400_y_no_toca_stock() throws Exception {
        for (String body : List.of("[]", "[{\"productId\":\"a\",\"qty\":0}]", "[{\"productId\":\"a\",\"qty\":-1}]")) {
            mvc.perform(post("/internal/stock/descontar").header("X-Internal-Key", "clave-interna")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(template);
    }

    @Test
    void restaurar_suma_stock() throws Exception {
        mvc.perform(post("/internal/stock/restaurar").header("X-Internal-Key", "clave-interna")
                .contentType(MediaType.APPLICATION_JSON).content(INTERNAL)).andExpect(status().isNoContent());
        ArgumentCaptor<UpdateDefinition> up = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(template).updateFirst(any(Query.class), up.capture(), eq(Producto.class));
        assertEquals(2, ((Document) up.getValue().getUpdateObject().get("$inc")).get("stock"));
    }

    @Test
    void descuento_es_actualizacion_condicionada_a_stock_mayor_o_igual_a_qty() throws Exception {
        when(template.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(producto("a", "v1"));
        mvc.perform(post("/internal/stock/descontar").header("X-Internal-Key", "clave-interna")
                .contentType(MediaType.APPLICATION_JSON).content(INTERNAL)).andExpect(status().isNoContent());
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(template).findAndModify(q.capture(), any(UpdateDefinition.class), eq(Producto.class));
        Document filtro = q.getValue().getQueryObject();
        assertEquals("a", filtro.get("_id"));
        assertEquals(new Document("$gte", 2), filtro.get("stock"));
    }

    @Test
    void salePrice_valida_en_post_y_put() throws Exception {
        when(repo.findById("a")).thenReturn(Optional.of(producto("a", "v1")));
        when(repo.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        for (String sale : List.of("-5", "0", "1000", "1500")) {
            String body = "{\"name\":\"N\",\"price\":1000,\"stock\":1,\"salePrice\":" + sale + "}";
            mvc.perform(post("/api/productos").header("Authorization", vendedor("v1"))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
            mvc.perform(put("/api/productos/a").header("Authorization", vendedor("v1"))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
        }
        for (String sale : List.of("null", "999")) {
            String body = "{\"name\":\"N\",\"price\":1000,\"stock\":1,\"salePrice\":" + sale + "}";
            mvc.perform(post("/api/productos").header("Authorization", vendedor("v1"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
            mvc.perform(put("/api/productos/a").header("Authorization", vendedor("v1"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        }
    }

    @Test
    void swagger_declara_bearerAuth_y_las_lecturas_publicas_sin_candado() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/productos'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/productos/{id}'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/internal/stock/descontar'].post.security").isEmpty());
    }
}
