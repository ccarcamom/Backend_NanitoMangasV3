package cl.nanitomangas.vendedores.controller;

import cl.nanitomangas.vendedores.dto.ItemStock;
import cl.nanitomangas.vendedores.exception.ApiException;
import cl.nanitomangas.vendedores.service.ProductoService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/internal/stock")
@SecurityRequirements // usa X-Internal-Key, no bearer
public class InternalStockController {

    private final ProductoService service;
    private final String internalKey;

    public InternalStockController(ProductoService service, @Value("${internal.key}") String internalKey) {
        this.service = service;
        this.internalKey = internalKey;
    }

    @PostMapping("/descontar")
    public ResponseEntity<Void> descontar(@Parameter(description = "Clave interna compartida") @RequestHeader(value = "X-Internal-Key", required = false) String key,
                                          @RequestBody @NotEmpty List<@Valid ItemStock> items) {
        verificar(key);
        service.descontar(items);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restaurar")
    public ResponseEntity<Void> restaurar(@Parameter(description = "Clave interna compartida") @RequestHeader(value = "X-Internal-Key", required = false) String key,
                                          @RequestBody @NotEmpty List<@Valid ItemStock> items) {
        verificar(key);
        service.restaurarStock(items);
        return ResponseEntity.noContent().build();
    }

    private void verificar(String key) {
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), internalKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Clave interna inválida");
        }
    }
}
