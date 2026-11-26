package cl.nanitomangas.clientes.service;

import cl.nanitomangas.clientes.client.VendedoresClient;
import cl.nanitomangas.clientes.client.VendedoresClient.Producto;
import cl.nanitomangas.clientes.client.VendedoresClient.StockItem;
import cl.nanitomangas.clientes.model.Pedido;
import cl.nanitomangas.clientes.model.Pedido.Buyer;
import cl.nanitomangas.clientes.repository.PedidoRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {
	public static final long ENVIO_DESPACHO = 3990, ENVIO_GRATIS_DESDE = 40000;

	public record ItemReq(@NotBlank String productId, @Min(1) int qty) {}
	public record PedidoReq(@NotNull @Valid Buyer buyer, @Email String email,
			@NotNull @Valid DeliveryReq delivery, @NotEmpty List<@Valid ItemReq> items) {}
	public record DeliveryReq(@NotNull @Pattern(regexp = "despacho|retiro") String method, @Valid Pedido.Address address) {}

	private final PedidoRepository repo;
	private final VendedoresClient vendedores;

	public PedidoService(PedidoRepository repo, VendedoresClient vendedores) {
		this.repo = repo;
		this.vendedores = vendedores;
	}

	/** userId nulo = compra de invitado. Orden: consultar y calcular, descontar, guardar (restaura si falla). */
	public Pedido crear(PedidoReq req, String userId) {
		var a = req.delivery().address();
		if ("despacho".equals(req.delivery().method()) && (a == null || blank(a.street()) || blank(a.region()) || blank(a.comuna())))
			throw new ApiException(400, "VALIDATION", "El despacho requiere calle, región y comuna");
		Map<String, Integer> qtys = new LinkedHashMap<>();
		req.items().forEach(i -> qtys.merge(i.productId(), i.qty(), Integer::sum));
		List<Pedido.Item> items = new ArrayList<>();
		long subtotal = 0;
		for (var e : qtys.entrySet()) {
			Producto p = vendedores.obtener(e.getKey()).orElseThrow(() ->
					new ApiException(409, "PRODUCT_NOT_FOUND:" + e.getKey(), "El producto ya no existe"));
			if (p.stock() < e.getValue())
				throw new ApiException(409, "OUT_OF_STOCK:" + e.getKey(), "Un producto ya no tiene stock suficiente");
			items.add(new Pedido.Item(e.getKey(), p.name(), p.precioFinal(), e.getValue(), p.sellerId()));
			subtotal += p.precioFinal() * e.getValue();
		}
		long shipping = "despacho".equals(req.delivery().method()) && subtotal < ENVIO_GRATIS_DESDE ? ENVIO_DESPACHO : 0;
		List<StockItem> stock = qtys.entrySet().stream().map(e -> new StockItem(e.getKey(), e.getValue())).toList();

		// shortcut: si descontar da timeout NO se restaura (quizá nunca se descontó y se sumaría stock de más); el stock puede quedar descontado sin pedido. Conciliar a mano o con un registro de compensaciones.
		vendedores.descontar(stock);
		try {
			return guardar(userId, req, items, subtotal, shipping);
		} catch (RuntimeException ex) {
			try { vendedores.restaurar(stock); } catch (RuntimeException r) { ex.addSuppressed(r); }
			throw ex;
		}
	}

	private static boolean blank(String s) { return s == null || s.isBlank(); }

	// shortcut: id = máximo existente + 1; dos creaciones simultáneas pueden chocar, insert() falla por _id duplicado y se reintenta una vez. Usar contador atómico si hay mucha concurrencia.
	private Pedido guardar(String userId, PedidoReq req, List<Pedido.Item> items, long subtotal, long shipping) {
		for (int intento = 1; ; intento++) {
			var p = new Pedido(siguienteId(), userId, req.email(), req.buyer(),
					new Pedido.Delivery(req.delivery().method(), req.delivery().address()),
					items, subtotal, shipping, subtotal + shipping, Instant.now(), "pagado");
			try {
				return repo.insert(p);
			} catch (DuplicateKeyException e) {
				if (intento >= 2) throw e;
			}
		}
	}

	private String siguienteId() {
		int n = repo.findFirstByOrderByIdDesc().map(p -> Integer.parseInt(p.id().substring(3))).orElse(0);
		return String.format("NM-%06d", n + 1);
	}

	public List<Pedido> mios(String userId) { return repo.findByUserIdOrderByCreatedAtDesc(userId); }

	public List<Pedido> todos() { return repo.findAllByOrderByCreatedAtDesc(); }

	/** Solo los ítems del vendedor; no se exponen los de otros. */
	public List<Pedido> deVendedor(String sellerId) {
		return repo.findByItemsSellerIdOrderByCreatedAtDesc(sellerId).stream()
				.map(p -> new Pedido(p.id(), p.userId(), p.email(), p.buyer(), p.delivery(),
						p.items().stream().filter(i -> sellerId.equals(i.sellerId())).toList(),
						p.subtotal(), p.shipping(), p.total(), p.createdAt(), p.status()))
				.toList();
	}

	public Pedido detalle(String id, String userId, boolean admin) {
		Pedido p = repo.findById(id).orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Pedido no encontrado"));
		if (!admin && !userId.equals(p.userId())) throw new ApiException(403, "FORBIDDEN", "Sin permiso");
		return p;
	}
}
