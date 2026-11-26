package cl.nanitomangas.clientes.model;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("pedidos")
public record Pedido(
		@Id String id, String userId, String email, Buyer buyer, Delivery delivery,
		List<Item> items, long subtotal, long shipping, long total, Instant createdAt, String status) {
	public record Buyer(@Size(max = 100) String name, String rut) {}
	public record Delivery(String method, Address address) {}
	public record Address(@Size(max = 120) String street, String region, String comuna) {}
	public record Item(String productId, String name, long unitPrice, int qty, String sellerId) {}
}
