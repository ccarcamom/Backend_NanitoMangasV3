package cl.nanitomangas.vendedores;

import cl.nanitomangas.vendedores.config.DataSeeder;
import cl.nanitomangas.vendedores.model.Producto;
import cl.nanitomangas.vendedores.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DataSeederTest {

    @Test
    @SuppressWarnings("unchecked")
    void siembra_17_productos_solo_si_la_coleccion_esta_vacia() {
        ProductoRepository repo = mock(ProductoRepository.class);
        when(repo.count()).thenReturn(0L, 17L);
        DataSeeder seeder = new DataSeeder(repo, "seed-vendedor");
        seeder.run();
        seeder.run();
        ArgumentCaptor<List<Producto>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).saveAll(cap.capture());
        assertEquals(17, cap.getValue().size());
        assertTrue(cap.getValue().stream().allMatch(p -> "seed-vendedor".equals(p.sellerId)));
        assertEquals("/img/OnePiece_1_IVREAAR.jpg", cap.getValue().get(0).image);
    }
}
