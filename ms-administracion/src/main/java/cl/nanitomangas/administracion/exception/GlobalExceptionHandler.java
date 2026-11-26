package cl.nanitomangas.administracion.exception;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    static ResponseEntity<Map<String, Object>> error(HttpStatus s, String code, String msg) {
        return ResponseEntity.status(s).body(Map.of("status", s.value(), "error", code, "message", msg));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        var f = e.getBindingResult().getFieldError();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION",
                f == null ? "Datos inválidos" : f.getField() + ": " + f.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> ilegible(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", "Cuerpo de la petición inválido");
    }

    // Carrera entre dos registros con el mismo correo: gana el índice único de Mongo.
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, Object>> duplicado(DuplicateKeyException e) {
        return error(HttpStatus.CONFLICT, "EMAIL_TAKEN", "El correo ya está registrado");
    }

    // 405, 404, 415...: los errores de Spring MVC conservan su estado con el JSON estándar.
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> generico(Exception e) throws Exception {
        // La seguridad debe seguir resolviendo 401/403 por su cuenta.
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) throw e;
        if (e instanceof ErrorResponse er) {
            HttpStatus s = HttpStatus.valueOf(er.getStatusCode().value());
            return error(s, s.name(), s.getReasonPhrase());
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor");
    }
}
