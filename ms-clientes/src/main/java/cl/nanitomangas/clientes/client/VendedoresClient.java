package cl.nanitomangas.clientes.client;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VendedoresClient {
	public record Producto(String id, String name, long price, Long salePrice, int stock, String sellerId) {
		public long precioFinal() { return salePrice != null ? salePrice : price; }
	}
	public record StockItem(String productId, int qty) {}
	/** Stock insuficiente en `productId`. */
	public static class SinStockException extends RuntimeException {
		public SinStockException(String productId) { super("OUT_OF_STOCK:" + productId); }
	}

	private final RestClient http;
	private final String internalKey;

	public VendedoresClient(@Value("${vendedores.url}") String url, @Value("${internal.key}") String internalKey,
			@Value("${vendedores.connect-timeout-ms:3000}") int connectMs, @Value("${vendedores.read-timeout-ms:5000}") int readMs) {
		var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		rf.setConnectTimeout(connectMs);
		rf.setReadTimeout(readMs);
		this.http = RestClient.builder().baseUrl(url).requestFactory(rf).build();
		this.internalKey = internalKey;
	}

	public Optional<Producto> obtener(String id) {
		// exchange: un 404 nunca deserializa su cuerpo de error como Producto
		return http.get().uri("/api/productos/{id}", id).exchange((rq, rs) -> {
			if (rs.getStatusCode().value() == 404) return Optional.<Producto>empty();
			if (rs.getStatusCode().isError()) throw new RestClientException("ms-vendedores respondió " + rs.getStatusCode());
			return Optional.ofNullable(rs.bodyTo(Producto.class));
		});
	}

	public void descontar(List<StockItem> items) {
		http.post().uri("/internal/stock/descontar").header("X-Internal-Key", internalKey).body(items).retrieve()
				.onStatus(s -> s.isSameCodeAs(HttpStatus.CONFLICT), (rq, rs) -> {
					String body = new String(rs.getBody().readAllBytes());
					var m = java.util.regex.Pattern.compile("OUT_OF_STOCK:([^\"\\s}]+)").matcher(body);
					throw new SinStockException(m.find() ? m.group(1) : "?");
				})
				.toBodilessEntity();
	}

	public void restaurar(List<StockItem> items) {
		http.post().uri("/internal/stock/restaurar").header("X-Internal-Key", internalKey).body(items).retrieve()
				.toBodilessEntity();
	}
}
