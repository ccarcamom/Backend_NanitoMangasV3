package cl.nanitomangas.clientes.controller;

import cl.nanitomangas.clientes.model.Pedido;
import cl.nanitomangas.clientes.service.PedidoService;
import cl.nanitomangas.clientes.service.PedidoService.PedidoReq;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
	private final PedidoService service;

	public PedidoController(PedidoService service) { this.service = service; }

	static boolean admin(Authentication a) {
		return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
	}

	/** Público: con token válido guarda el userId; sin token es invitado. */
	@PostMapping
	@io.swagger.v3.oas.annotations.security.SecurityRequirements // token opcional: sin candado
	@ResponseStatus(HttpStatus.CREATED)
	public Pedido crear(@Valid @RequestBody PedidoReq req, Authentication auth) {
		boolean invitado = auth == null || auth instanceof AnonymousAuthenticationToken;
		return service.crear(req, invitado ? null : auth.getName());
	}

	@GetMapping("/mios")
	public List<Pedido> mios(Authentication auth) { return service.mios(auth.getName()); }

	@GetMapping("/vendedor")
	@PreAuthorize("hasRole('VENDEDOR')")
	public List<Pedido> vendedor(Authentication auth) { return service.deVendedor(auth.getName()); }

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<Pedido> todos() { return service.todos(); }

	@GetMapping("/{id}")
	public Pedido detalle(@PathVariable String id, Authentication auth) {
		return service.detalle(id, auth.getName(), admin(auth));
	}
}
