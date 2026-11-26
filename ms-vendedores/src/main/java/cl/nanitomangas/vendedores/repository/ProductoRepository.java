package cl.nanitomangas.vendedores.repository;

import cl.nanitomangas.vendedores.model.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductoRepository extends MongoRepository<Producto, String> {
    List<Producto> findBySellerId(String sellerId);
}
