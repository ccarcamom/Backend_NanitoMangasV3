package cl.nanitomangas.administracion.service;

import cl.nanitomangas.administracion.dto.Dtos.*;
import cl.nanitomangas.administracion.exception.ApiException;
import cl.nanitomangas.administracion.model.Role;
import cl.nanitomangas.administracion.model.Usuario;
import cl.nanitomangas.administracion.repository.UsuarioRepository;
import cl.nanitomangas.administracion.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthService {
    static final Duration ACCESS = Duration.ofMinutes(15);
    static final Duration REFRESH = Duration.ofDays(7);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthService(UsuarioRepository repo, PasswordEncoder encoder, JwtUtil jwt) {
        this.repo = repo; this.encoder = encoder; this.jwt = jwt;
    }

    private static String norm(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private static ApiException credenciales() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
    }

    public UsuarioDto registrar(RegisterRequest r) {
        Role role = Role.CLIENTE;
        if (r.role() != null && !r.role().isBlank()) {
            try { role = Role.valueOf(r.role().trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { role = Role.ADMIN; }
            if (role == Role.ADMIN)
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "El rol debe ser CLIENTE o VENDEDOR");
        }
        String email = norm(r.email());
        if (repo.findByEmail(email).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "El correo ya está registrado");
        Usuario u = new Usuario();
        u.setName(r.name().trim()); u.setEmail(email); u.setRole(role);
        u.setPasswordHash(encoder.encode(r.password()));
        return UsuarioDto.de(repo.save(u));
    }

    public AuthResponse login(LoginRequest r) {
        Usuario u = repo.findByEmail(norm(r.email())).orElseThrow(AuthService::credenciales);
        if (!u.isActive() || !encoder.matches(r.password(), u.getPasswordHash())) throw credenciales();
        return par(u);
    }

    public AuthResponse refrescar(String refreshToken) {
        Usuario u;
        try {
            Claims c = jwt.verificar(refreshToken);
            if (!"refresh".equals(c.get("typ", String.class))) throw credenciales();
            u = repo.findById(c.getSubject()).orElseThrow(AuthService::credenciales);
        } catch (JwtException | IllegalArgumentException e) {
            throw credenciales();
        }
        if (!u.isActive()) throw credenciales();
        return par(u);
    }

    private AuthResponse par(Usuario u) {
        String role = u.getRole().name();
        return new AuthResponse(jwt.crear(u.getId(), u.getEmail(), role, "access", ACCESS),
                jwt.crear(u.getId(), u.getEmail(), role, "refresh", REFRESH), UsuarioDto.de(u));
    }
}
