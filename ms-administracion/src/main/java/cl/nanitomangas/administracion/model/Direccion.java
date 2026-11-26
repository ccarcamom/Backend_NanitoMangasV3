package cl.nanitomangas.administracion.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Direccion(@NotBlank @Size(max = 120) String street, @NotBlank String region, @NotBlank String comuna) {}
