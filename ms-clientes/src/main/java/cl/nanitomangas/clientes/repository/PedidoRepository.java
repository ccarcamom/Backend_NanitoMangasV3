package cl.nanitomangas.clientes.repository;

import cl.nanitomangas.clientes.model.Pedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
	List<Pedido> findByUserIdOrderByCreatedAtDesc(String userId);
	List<Pedido> findByItemsSellerIdOrderByCreatedAtDesc(String sellerId);
	List<Pedido> findAllByOrderByCreatedAtDesc();
	Optional<Pedido> findFirstByOrderByIdDesc();
}
