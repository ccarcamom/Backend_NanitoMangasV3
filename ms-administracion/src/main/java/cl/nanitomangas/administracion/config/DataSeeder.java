package cl.nanitomangas.administracion.config;

import cl.nanitomangas.administracion.model.Role;
import cl.nanitomangas.administracion.model.Usuario;
import cl.nanitomangas.administracion.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    public DataSeeder(UsuarioRepository repo, PasswordEncoder encoder) { this.repo = repo; this.encoder = encoder; }

    @Override
    public void run(String... args) {
        sembrar("seed-admin", "Administrador", "admin@nanitomangas.cl", "Admin1234", Role.ADMIN);
        sembrar("seed-vendedor", "Vendedor", "vendedor@nanitomangas.cl", "Vendedor1234", Role.VENDEDOR);
    }

    private void sembrar(String id, String name, String email, String pass, Role role) {
        if (repo.findById(id).isPresent()) return;
        Usuario u = new Usuario();
        u.setId(id); u.setName(name); u.setEmail(email); u.setRole(role);
        u.setPasswordHash(encoder.encode(pass));
        repo.save(u);
    }
}
