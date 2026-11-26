package cl.nanitomangas.administracion;

import cl.nanitomangas.administracion.model.Role;
import cl.nanitomangas.administracion.model.Usuario;
import cl.nanitomangas.administracion.repository.UsuarioRepository;
import cl.nanitomangas.administracion.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"})
@AutoConfigureMockMvc
class AdministracionTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper om;
    @MockitoBean UsuarioRepository repo;

    // Repositorio falso en memoria: el Mongo real no existe en los tests.
    Map<String, Usuario> db = new HashMap<>();

    @BeforeEach
    void setUp() {
        db.clear();
        when(repo.save(any())).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID().toString());
            db.put(u.getId(), u);
            return u;
        });
        when(repo.findByEmail(anyString())).thenAnswer(i ->
                db.values().stream().filter(u -> u.getEmail().equals(i.getArgument(0))).findFirst());
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(db.get(i.<String>getArgument(0))));
        org.mockito.Mockito.doAnswer(i -> db.remove(i.<Usuario>getArgument(0).getId())).when(repo).delete(any());
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(db.values()));
    }

    ResultActions enviar(String url, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    ResultActions registrar(String email) throws Exception {
        return enviar("/auth/register", Map.of("name", "Ana", "email", email, "password", "Secreta123"));
    }

    String access(String id, Role role) {
        return jwt.crear(id, "x@x.cl", role.name(), "access", Duration.ofMinutes(5));
    }

    @Test
    void registro_crea_cliente_por_defecto() throws Exception {
        registrar("ana@mail.cl").andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.email").value("ana@mail.cl"))
                .andExpect(jsonPath("$.active").value(true));
        assertEquals(1, db.size());
    }

    @Test
    void registro_acepta_vendedor_y_rechaza_admin() throws Exception {
        enviar("/auth/register", Map.of("name", "V", "email", "v@mail.cl", "password", "Secreta123", "role", "VENDEDOR"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("VENDEDOR"));
        enviar("/auth/register", Map.of("name", "A", "email", "a@mail.cl", "password", "Secreta123", "role", "ADMIN"))
                .andExpect(status().isBadRequest());
        assertEquals(1, db.size());
    }

    @Test
    void registro_con_correo_repetido_da_409_EMAIL_TAKEN() throws Exception {
        registrar("ana@mail.cl").andExpect(status().isCreated());
        registrar("ana@mail.cl").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN")).andExpect(jsonPath("$.status").value(409));
        registrar("  ANA@Mail.CL ").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
        assertEquals(1, db.size());
    }

    @Test
    void login_correcto_devuelve_access_y_refresh() throws Exception {
        registrar("ana@mail.cl");
        enviar("/auth/login", Map.of("email", " ANA@mail.cl", "password", "Secreta123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("ana@mail.cl"));
    }

    @Test
    void login_malo_da_401_INVALID_CREDENTIALS() throws Exception {
        registrar("ana@mail.cl");
        enviar("/auth/login", Map.of("email", "ana@mail.cl", "password", "mala"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        enviar("/auth/login", Map.of("email", "nadie@mail.cl", "password", "Secreta123"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void usuario_inactivo_no_puede_iniciar_sesion() throws Exception {
        registrar("ana@mail.cl");
        db.values().forEach(u -> u.setActive(false));
        enviar("/auth/login", Map.of("email", "ana@mail.cl", "password", "Secreta123"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refresh_con_refresh_valido_da_par_nuevo() throws Exception {
        registrar("ana@mail.cl");
        String body = enviar("/auth/login", Map.of("email", "ana@mail.cl", "password", "Secreta123"))
                .andReturn().getResponse().getContentAsString();
        String refresh = om.readTree(body).get("refreshToken").asText();
        enviar("/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refresh_con_access_token_da_401() throws Exception {
        enviar("/auth/refresh", Map.of("refreshToken", access("u1", Role.CLIENTE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_token_no_sirve_como_access_token() throws Exception {
        String refresh = jwt.crear("u1", "x@x.cl", "ADMIN", "refresh", Duration.ofDays(1));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void admin_usuarios_sin_token_da_401() throws Exception {
        mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void admin_usuarios_con_rol_CLIENTE_da_403() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + access("u1", Role.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void token_con_firma_ajena_da_401() throws Exception {
        String ajeno = Jwts.builder().subject("u1").claim("role", "ADMIN").claim("typ", "access")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("otro-secreto-otro-secreto-otro-secreto-123".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_con_role_alterado_da_401() throws Exception {
        String[] p = access("u1", Role.CLIENTE).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8)
                .replace("CLIENTE", "ADMIN");
        String falso = p[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + p[2];
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_vencido_da_401() throws Exception {
        String viejo = jwt.crear("u1", "x@x.cl", "ADMIN", "access", Duration.ofSeconds(-10));
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + viejo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void la_respuesta_nunca_incluye_passwordHash() throws Exception {
        registrar("ana@mail.cl").andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("passwordHash"))));
        Usuario admin = nuevo("adm", "adm@mail.cl", Role.ADMIN);
        String tok = access(admin.getId(), Role.ADMIN);
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("$2a$"))));
        mvc.perform(get("/api/admin/usuarios/adm").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        enviar("/auth/login", Map.of("email", "ana@mail.cl", "password", "Secreta123"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test
    void me_y_edicion_de_perfil_propio() throws Exception {
        Usuario u = nuevo("u1", "u1@mail.cl", Role.CLIENTE);
        String h = "Bearer " + access("u1", Role.CLIENTE);
        mvc.perform(put("/api/usuarios/me").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\",\"rut\":\"12345678-9\",\"role\":\"ADMIN\","
                                + "\"address\":{\"street\":\"Calle 1\",\"region\":\"RM\",\"comuna\":\"Maipú\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nuevo"))
                .andExpect(jsonPath("$.rut").value("12345678-9")).andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.address.street").value("Calle 1"))
                .andExpect(jsonPath("$.address.comuna").value("Maipú"));
        mvc.perform(get("/auth/me").header("Authorization", h)).andExpect(jsonPath("$.address.region").value("RM"));
        assertEquals(Role.CLIENTE, u.getRole());
    }

    @Test
    void perfil_con_direccion_o_rut_invalidos_da_400() throws Exception {
        nuevo("u1", "u1@mail.cl", Role.CLIENTE);
        String h = "Bearer " + access("u1", Role.CLIENTE);
        for (String body : new String[]{
                "{\"address\":{\"street\":\"Calle 1\",\"region\":\"RM\",\"comuna\":\"\"}}",
                "{\"address\":{\"street\":\"" + "x".repeat(121) + "\",\"region\":\"RM\",\"comuna\":\"M\"}}",
                "{\"rut\":\"123-4\"}", "{\"rut\":\"12.345.678-9\"}", "{\"rut\":\"abc\"}"}) {
            mvc.perform(put("/api/usuarios/me").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
        }
        mvc.perform(put("/api/usuarios/me").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rut\":\"1234567-K\"}")).andExpect(status().isOk());
    }

    @Test
    void password_debe_tener_8_a_72_con_letra_y_numero() throws Exception {
        for (String p : new String[]{"Abc1234", "Abcdefgh", "12345678"}) {
            enviar("/auth/register", Map.of("name", "A", "email", "a@mail.cl", "password", p))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password")));
        }
        assertEquals(0, db.size());
        enviar("/auth/register", Map.of("name", "A", "email", "a@mail.cl", "password", "Abc12345"))
                .andExpect(status().isCreated());
    }

    @Test
    void el_token_se_firma_con_HS256() throws Exception {
        String header = new String(Base64.getUrlDecoder().decode(access("u1", Role.CLIENTE).split("\\.")[0]),
                StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertEquals("HS256", om.readTree(header).get("alg").asText());
    }

    @Test
    void swagger_declara_bearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty());
    }

    @Test
    void admin_cambia_role_y_active_y_borra() throws Exception {
        nuevo("adm", "adm@mail.cl", Role.ADMIN);
        nuevo("u1", "u1@mail.cl", Role.CLIENTE);
        String h = "Bearer " + access("adm", Role.ADMIN);
        mvc.perform(put("/api/admin/usuarios/u1").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VENDEDOR\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VENDEDOR"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/admin/usuarios/u1").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"JEFE\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/usuarios/nope").header("Authorization", h)).andExpect(status().isNotFound());
    }

    @Test
    void admin_no_se_borra_a_si_mismo_ni_a_seed_admin() throws Exception {
        nuevo("adm", "adm@mail.cl", Role.ADMIN);
        nuevo("seed-admin", "seed@mail.cl", Role.ADMIN);
        String h = "Bearer " + access("adm", Role.ADMIN);
        mvc.perform(delete("/api/admin/usuarios/adm").header("Authorization", h))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("FORBIDDEN_SELF_OR_SEED"));
        mvc.perform(delete("/api/admin/usuarios/seed-admin").header("Authorization", h))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("FORBIDDEN_SELF_OR_SEED"));
        assertEquals(2, db.size());
    }

    @Test
    void admin_no_se_baja_de_rol_ni_se_desactiva_ni_toca_a_seed_admin() throws Exception {
        nuevo("adm", "adm@mail.cl", Role.ADMIN);
        nuevo("seed-admin", "seed@mail.cl", Role.ADMIN);
        String h = "Bearer " + access("adm", Role.ADMIN);
        for (String[] c : new String[][]{{"adm", "{\"role\":\"CLIENTE\"}"}, {"adm", "{\"active\":false}"},
                {"seed-admin", "{\"active\":false}"}, {"seed-admin", "{\"role\":\"VENDEDOR\"}"}}) {
            mvc.perform(put("/api/admin/usuarios/" + c[0]).header("Authorization", h)
                            .contentType(MediaType.APPLICATION_JSON).content(c[1]))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("FORBIDDEN_SELF_OR_SEED"));
        }
        assertEquals(Role.ADMIN, db.get("adm").getRole());
        assertEquals(true, db.get("seed-admin").isActive());
    }

    @Test
    void admin_si_cambia_rol_de_otro_y_lo_borra() throws Exception {
        nuevo("adm", "adm@mail.cl", Role.ADMIN);
        nuevo("u1", "u1@mail.cl", Role.CLIENTE);
        String h = "Bearer " + access("adm", Role.ADMIN);
        mvc.perform(put("/api/admin/usuarios/u1").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/usuarios/adm").header("Authorization", h).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\",\"active\":true}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/usuarios/u1").header("Authorization", h)).andExpect(status().isNoContent());
        assertEquals(1, db.size());
    }

    @Test
    void errores_401_se_envian_en_utf8() throws Exception {
        String ct = mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized()).andReturn().getResponse()
                .getHeader("Content-Type");
        assertEquals("application/json;charset=UTF-8", ct);
    }

    @Test
    void metodo_no_permitido_da_405_con_json_estandar() throws Exception {
        mvc.perform(get("/auth/login")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405)).andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void error_inesperado_da_500_con_json_estandar() throws Exception {
        nuevo("adm", "adm@mail.cl", Role.ADMIN);
        when(repo.findAll()).thenThrow(new IllegalStateException("boom"));
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + access("adm", Role.ADMIN)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void validacion_indica_el_campo_y_limita_password_a_72() throws Exception {
        enviar("/auth/register", Map.of("name", "A", "email", "a@mail.cl", "password", "a1" + "x".repeat(71)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password")));
        enviar("/auth/register", Map.of("name", "A", "email", "a@mail.cl", "password", "a1" + "x".repeat(70)))
                .andExpect(status().isCreated());
    }

    @Test
    void email_declara_indice_unico_con_nombre_estable() throws Exception {
        var ind = Usuario.class.getDeclaredField("email").getAnnotation(
                org.springframework.data.mongodb.core.index.Indexed.class);
        org.junit.jupiter.api.Assertions.assertTrue(ind != null && ind.unique());
        assertEquals("email", ind.name());
    }

    Usuario nuevo(String id, String email, Role role) {
        Usuario u = new Usuario();
        u.setId(id); u.setEmail(email); u.setName(id); u.setRole(role); u.setPasswordHash("$2a$x"); u.setActive(true);
        db.put(id, u);
        return u;
    }
}
