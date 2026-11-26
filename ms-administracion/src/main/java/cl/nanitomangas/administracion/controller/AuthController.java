package cl.nanitomangas.administracion.controller;

import cl.nanitomangas.administracion.dto.Dtos.*;
import cl.nanitomangas.administracion.service.AuthService;
import cl.nanitomangas.administracion.service.UsuarioService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;
    private final UsuarioService usuarios;

    public AuthController(AuthService auth, UsuarioService usuarios) { this.auth = auth; this.usuarios = usuarios; }

    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<UsuarioDto> register(@Valid @RequestBody RegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.registrar(r));
    }

    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @SecurityRequirements
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest r) { return auth.refrescar(r.refreshToken()); }

    @GetMapping("/me")
    public UsuarioDto me(Authentication a) { return UsuarioDto.de(usuarios.buscar(a.getName())); }
}
