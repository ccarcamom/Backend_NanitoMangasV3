package cl.nanitomangas.vendedores.controller;

import cl.nanitomangas.vendedores.exception.ApiException;
import cl.nanitomangas.vendedores.model.Producto;
import cl.nanitomangas.vendedores.service.ProductoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @SecurityRequirements
    @GetMapping
    public List<Producto> listar(@RequestParam(required = false) String categoryId,
                                 @RequestParam(required = false) String genre,
                                 @RequestParam(required = false) String q) {
        return service.listar(categoryId, genre, q);
    }

    @GetMapping("/mios")
    public List<Producto> mios(Authentication auth) {
        return service.deVendedor(auth.getName());
    }

    @SecurityRequirements
    @GetMapping("/{id}")
    public Producto detalle(@PathVariable String id) {
        return service.buscar(id).orElseThrow(this::noExiste);
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@Valid @RequestBody Producto body, Authentication auth) {
        body.id = null;
        if (!esAdmin(auth) || body.sellerId == null) body.sellerId = auth.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.guardar(body));
    }

    @PutMapping("/{id}")
    public Producto editar(@PathVariable String id, @Valid @RequestBody Producto body, Authentication auth) {
        Producto actual = duenoOAdmin(id, auth);
        body.id = id;
        body.sellerId = esAdmin(auth) && body.sellerId != null ? body.sellerId : actual.sellerId;
        return service.guardar(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(@PathVariable String id, Authentication auth) {
        duenoOAdmin(id, auth);
        service.borrar(id);
        return ResponseEntity.noContent().build();
    }

    private Producto duenoOAdmin(String id, Authentication auth) {
        Producto p = service.buscar(id).orElseThrow(this::noExiste);
        if (!esAdmin(auth) && !auth.getName().equals(p.sellerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "El producto pertenece a otro vendedor");
        }
        return p;
    }

    private static boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private ApiException noExiste() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Producto no encontrado");
    }
}
