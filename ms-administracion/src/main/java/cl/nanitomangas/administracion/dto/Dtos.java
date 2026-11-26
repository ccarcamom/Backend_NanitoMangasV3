package cl.nanitomangas.administracion.dto;

import cl.nanitomangas.administracion.model.Direccion;
import cl.nanitomangas.administracion.model.Role;
import cl.nanitomangas.administracion.model.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** DTOs de entrada y salida. UsuarioDto nunca incluye passwordHash. */
public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank String name, @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72)
                                  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "debe tener al menos una letra y un número")
                                  String password, String role) {
        public RegisterRequest { if (email != null) email = email.trim(); }
    }
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record PerfilRequest(String name,
                                @Pattern(regexp = "^(\\d{7,8}-[\\dkK])?$", message = "formato inválido (ej. 12345678-9)") String rut,
                                @Valid Direccion address) {}
    public record AdminUpdateRequest(Role role, Boolean active) {}

    public record UsuarioDto(String id, String name, String email, Role role, String rut, Direccion address,
                             boolean active, Instant createdAt) {
        public static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getRut(), u.getAddress(),
                    u.isActive(), u.getCreatedAt());
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, UsuarioDto user) {}
}
