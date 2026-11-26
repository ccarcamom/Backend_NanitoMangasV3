package cl.nanitomangas.clientes.repository;

import cl.nanitomangas.clientes.model.Meme;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MemeRepository extends MongoRepository<Meme, String> {
	List<Meme> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
