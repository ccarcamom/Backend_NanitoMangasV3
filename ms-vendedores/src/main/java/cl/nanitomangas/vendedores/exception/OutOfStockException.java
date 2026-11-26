package cl.nanitomangas.vendedores.exception;

public class OutOfStockException extends RuntimeException {
    public OutOfStockException(String productId) {
        super("OUT_OF_STOCK:" + productId);
    }
}
