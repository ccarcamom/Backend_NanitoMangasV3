package cl.nanitomangas.administracion.service;

import cl.nanitomangas.administracion.dto.Dtos.*;
import cl.nanitomangas.administracion.exception.ApiException;
import cl.nanitomangas.administracion.model.Role;
import cl.nanitomangas.administracion.model.Usuario;
import cl.nanitomangas.administracion.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {
    private final UsuarioRepository repo;

    public UsuarioService(UsuarioRepository repo) { this.repo = repo; }

    public Usuario buscar(String id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Usuario no encontrado"));
    }

    public List<UsuarioDto> listar() { return repo.findAll().stream().map(UsuarioDto::de).toList(); }

    public UsuarioDto editarPerfil(String id, PerfilRequest r) {
        Usuario u = buscar(id);
        if (r.name() != null && !r.name().isBlank()) u.setName(r.name());
        if (r.rut() != null) u.setRut(r.rut());
        if (r.address() != null) u.setAddress(r.address());
        return UsuarioDto.de(repo.save(u));
    }

    // Evita dejar la plataforma sin admin: ni uno mismo ni las cuentas semilla.
    private void protegido(String callerId, String id) {
        if (id.equals(callerId) || id.startsWith("seed-"))
            throw new ApiException(HttpStatus.CONFLICT, "FORBIDDEN_SELF_OR_SEED",
                    "No puedes borrar, desactivar ni bajar de rol esta cuenta");
    }

    public UsuarioDto adminEditar(String callerId, String id, AdminUpdateRequest r) {
        if (Boolean.FALSE.equals(r.active()) || (r.role() != null && r.role() != Role.ADMIN)) protegido(callerId, id);
        Usuario u = buscar(id);
        if (r.role() != null) u.setRole(r.role());
        if (r.active() != null) u.setActive(r.active());
        return UsuarioDto.de(repo.save(u));
    }

    public void borrar(String callerId, String id) {
        protegido(callerId, id);
        repo.delete(buscar(id));
    }
}
