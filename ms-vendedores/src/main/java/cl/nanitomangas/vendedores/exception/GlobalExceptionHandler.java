package cl.nanitomangas.vendedores.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public static Map<String, Object> body(int status, String error, String message) {
        return Map.of("status", status, "error", error, "message", message);
    }

    private static ResponseEntity<Map<String, Object>> resp(HttpStatus s, String error, String message) {
        return ResponseEntity.status(s).body(body(s.value(), error, message));
    }

    @ExceptionHandler(OutOfStockException.class)
    ResponseEntity<Map<String, Object>> sinStock(OutOfStockException e) {
        return resp(HttpStatus.CONFLICT, e.getMessage(), "Producto sin stock suficiente");
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return resp(e.status, e.code, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, Object>> invalido(Exception e) {
        return resp(HttpStatus.BAD_REQUEST, "VALIDATION", "Datos inválidos");
    }
}
