package cl.nanitomangas.vendedores.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Campos públicos: el documento es un contenedor de datos sin lógica. */
@Document("productos")
public class Producto {
    @Id public String id;
    @NotBlank public String name;
    public String brand;
    public String categoryId;
    public String genre;
    @NotNull @Min(0) public Integer price;
    public Integer salePrice;
    @NotNull @Min(0) public Integer stock;
    public String image;
    public String description;
    public Boolean isNew;
    public Boolean isBestSeller;
    public Boolean isRestock;
    public String sellerId;

    /** salePrice nulo, o mayor que 0 y menor que price. */
    @JsonIgnore
    @AssertTrue
    public boolean isSalePriceValido() {
        return salePrice == null || price == null || (salePrice > 0 && salePrice < price);
    }
}
