package cl.nanitomangas.administracion.controller;

import cl.nanitomangas.administracion.dto.Dtos.*;
import cl.nanitomangas.administracion.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UsuarioController {
    private final UsuarioService svc;

    public UsuarioController(UsuarioService svc) { this.svc = svc; }

    @PutMapping("/api/usuarios/me")
    public UsuarioDto editarPerfil(Authentication a, @jakarta.validation.Valid @RequestBody PerfilRequest r) {
        return svc.editarPerfil(a.getName(), r);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/api/admin/usuarios")
    public List<UsuarioDto> listar() { return svc.listar(); }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/api/admin/usuarios/{id}")
    public UsuarioDto detalle(@PathVariable String id) { return UsuarioDto.de(svc.buscar(id)); }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/api/admin/usuarios/{id}")
    public UsuarioDto adminEditar(Authentication a, @PathVariable String id, @RequestBody AdminUpdateRequest r) {
        return svc.adminEditar(a.getName(), id, r);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/api/admin/usuarios/{id}")
    public ResponseEntity<Void> borrar(Authentication a, @PathVariable String id) {
        svc.borrar(a.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
