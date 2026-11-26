package cl.nanitomangas.vendedores.service;

import cl.nanitomangas.vendedores.dto.ItemStock;
import cl.nanitomangas.vendedores.exception.OutOfStockException;
import cl.nanitomangas.vendedores.model.Producto;
import cl.nanitomangas.vendedores.repository.ProductoRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ProductoService {

    private final ProductoRepository repo;
    private final MongoTemplate template;

    public ProductoService(ProductoRepository repo, MongoTemplate template) {
        this.repo = repo;
        this.template = template;
    }

    public List<Producto> listar(String categoryId, String genre, String q) {
        Query query = new Query();
        if (categoryId != null && !categoryId.isBlank()) query.addCriteria(Criteria.where("categoryId").is(categoryId));
        if (genre != null && !genre.isBlank()) query.addCriteria(Criteria.where("genre").is(genre));
        if (q != null && !q.isBlank()) {
            query.addCriteria(Criteria.where("name").regex(Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE)));
        }
        return template.find(query, Producto.class);
    }

    /** Un producto a la vez, con filtro atómico stock >= qty; si uno falla se devuelven los ya descontados. */
    public void descontar(List<ItemStock> items) {
        List<ItemStock> hechos = new ArrayList<>();
        for (ItemStock it : items) {
            Query condicionada = Query.query(Criteria.where("_id").is(it.productId()).and("stock").gte(it.qty()));
            Producto actualizado = template.findAndModify(condicionada, new Update().inc("stock", -it.qty()), Producto.class);
            if (actualizado == null) {
                restaurarStock(hechos);
                throw new OutOfStockException(it.productId());
            }
            hechos.add(it);
        }
    }

    public void restaurarStock(List<ItemStock> items) {
        for (ItemStock it : items) {
            template.updateFirst(Query.query(Criteria.where("_id").is(it.productId())),
                    new Update().inc("stock", it.qty()), Producto.class);
        }
    }

    public Producto guardar(Producto p) { return repo.save(p); }
    public java.util.Optional<Producto> buscar(String id) { return repo.findById(id); }
    public List<Producto> deVendedor(String sellerId) { return repo.findBySellerId(sellerId); }
    public void borrar(String id) { repo.deleteById(id); }
}
