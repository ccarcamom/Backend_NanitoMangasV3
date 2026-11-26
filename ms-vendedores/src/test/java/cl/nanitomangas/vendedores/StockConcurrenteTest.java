package cl.nanitomangas.vendedores;

import cl.nanitomangas.vendedores.exception.OutOfStockException;
import cl.nanitomangas.vendedores.dto.ItemStock;
import cl.nanitomangas.vendedores.model.Producto;
import cl.nanitomangas.vendedores.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class StockConcurrenteTest {

    @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Test
    void dos_descuentos_concurrentes_de_la_ultima_unidad_dejan_stock_0_y_uno_falla() throws Exception {
        MongoTemplate template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(mongo.getReplicaSetUrl("nm_test")));
        Producto p = new Producto();
        p.id = "ultimo"; p.name = "Ultimo"; p.price = 1000; p.stock = 1;
        template.save(p);
        ProductoService service = new ProductoService(null, template);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> r = List.of(0, 1).stream().map(i -> pool.submit(() -> {
            salida.await();
            try { service.descontar(List.of(new ItemStock("ultimo", 1))); return true; }
            catch (OutOfStockException e) { return false; }
        })).toList();
        salida.countDown();
        int ganadores = 0;
        for (Future<Boolean> f : r) if (f.get(10, TimeUnit.SECONDS)) ganadores++;
        pool.shutdown();

        assertEquals(1, ganadores);
        assertEquals(0, template.findById("ultimo", Producto.class).stock);
    }
}
