package cl.nanitomangas.vendedores.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ItemStock(@NotBlank String productId, @Min(1) int qty) {}
