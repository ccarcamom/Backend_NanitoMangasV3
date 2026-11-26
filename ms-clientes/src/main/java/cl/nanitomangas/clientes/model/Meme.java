package cl.nanitomangas.clientes.model;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("memes")
public record Meme(
		@Id String id, String ownerId, String productId, String language, int pageIndex,
		List<Bubble> bubbles, Instant createdAt) {
	public record Bubble(
			@DecimalMin("0.0") @DecimalMax("1.0") double x, @DecimalMin("0.0") @DecimalMax("1.0") double y,
			@DecimalMin("0.0") @DecimalMax("1.0") double w, @DecimalMin("0.0") @DecimalMax("1.0") double h, String text,
			String fontFamily, int fontSize, String color, String background) {}
}
