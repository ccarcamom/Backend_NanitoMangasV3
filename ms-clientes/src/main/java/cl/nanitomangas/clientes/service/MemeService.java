package cl.nanitomangas.clientes.service;

import cl.nanitomangas.clientes.model.Meme;
import cl.nanitomangas.clientes.model.Meme.Bubble;
import cl.nanitomangas.clientes.repository.MemeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemeService {
	public record MemeReq(@NotBlank String productId, String language, @Min(0) int pageIndex,
			@NotNull List<@Valid Bubble> bubbles) {}

	private final MemeRepository repo;

	public MemeService(MemeRepository repo) { this.repo = repo; }

	public List<Meme> listar(String ownerId) { return repo.findByOwnerIdOrderByCreatedAtDesc(ownerId); }

	public Meme crear(MemeReq r, String ownerId) {
		return repo.save(new Meme(null, ownerId, r.productId(), r.language(), r.pageIndex(), r.bubbles(), Instant.now()));
	}

	public Meme detalle(String id, String userId, boolean admin) { return propio(id, userId, admin); }

	public Meme editar(String id, MemeReq r, String userId) {
		Meme m = propio(id, userId, false);
		return repo.save(new Meme(m.id(), m.ownerId(), r.productId(), r.language(), r.pageIndex(), r.bubbles(), m.createdAt()));
	}

	public void borrar(String id, String userId, boolean admin) { repo.delete(propio(id, userId, admin)); }

	private Meme propio(String id, String userId, boolean admin) {
		Meme m = repo.findById(id).orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Meme no encontrado"));
		if (!admin && !userId.equals(m.ownerId())) throw new ApiException(403, "FORBIDDEN", "Sin permiso");
		return m;
	}
}
