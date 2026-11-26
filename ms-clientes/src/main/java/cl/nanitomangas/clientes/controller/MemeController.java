package cl.nanitomangas.clientes.controller;

import cl.nanitomangas.clientes.model.Meme;
import cl.nanitomangas.clientes.service.MemeService;
import cl.nanitomangas.clientes.service.MemeService.MemeReq;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memes")
public class MemeController {
	private final MemeService service;

	public MemeController(MemeService service) { this.service = service; }

	@GetMapping
	@PreAuthorize("hasRole('CLIENTE')")
	public List<Meme> listar(Authentication a) { return service.listar(a.getName()); }

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('CLIENTE')")
	public Meme crear(@Valid @RequestBody MemeReq r, Authentication a) { return service.crear(r, a.getName()); }

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
	public Meme detalle(@PathVariable String id, Authentication a) {
		return service.detalle(id, a.getName(), PedidoController.admin(a));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('CLIENTE')")
	public Meme editar(@PathVariable String id, @Valid @RequestBody MemeReq r, Authentication a) {
		return service.editar(id, r, a.getName());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
	public void borrar(@PathVariable String id, Authentication a) {
		service.borrar(id, a.getName(), PedidoController.admin(a));
	}
}
