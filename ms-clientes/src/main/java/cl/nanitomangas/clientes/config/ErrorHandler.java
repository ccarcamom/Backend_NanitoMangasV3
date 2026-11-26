package cl.nanitomangas.clientes.config;

import cl.nanitomangas.clientes.client.VendedoresClient.SinStockException;
import cl.nanitomangas.clientes.service.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.net.SocketTimeoutException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ErrorHandler {
	private static ResponseEntity<Map<String, Object>> r(int s, String e, String m) {
		return ResponseEntity.status(s).body(Map.of("status", s, "error", e, "message", m));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Map<String, Object>> api(ApiException e) { return r(e.status, e.error, e.getMessage()); }

	@ExceptionHandler(SinStockException.class)
	ResponseEntity<Map<String, Object>> stock(SinStockException e) {
		return r(409, e.getMessage(), "Un producto ya no tiene stock suficiente");
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	ResponseEntity<Map<String, Object>> invalido(Exception e) { return r(400, "VALIDATION", "Datos inválidos"); }

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Map<String, Object>> denegado(AccessDeniedException e) { return r(403, "FORBIDDEN", "Sin permiso"); }

	@ExceptionHandler(RestClientException.class)
	ResponseEntity<Map<String, Object>> vendedores(RestClientException e) {
		boolean timeout = e instanceof ResourceAccessException && e.getCause() instanceof SocketTimeoutException;
		return r(HttpStatus.BAD_GATEWAY.value(), timeout ? "UPSTREAM_TIMEOUT" : "UPSTREAM_ERROR", "ms-vendedores no responde");
	}

	/** 404, 405, 415... de Spring conservan su status. */
	@ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class, ResponseStatusException.class})
	ResponseEntity<Map<String, Object>> spring(Exception e) {
		int s = ((ErrorResponse) e).getStatusCode().value();
		HttpStatus h = HttpStatus.resolve(s);
		return r(s, h == null ? "ERROR" : h.name(), h == null ? "Error" : h.getReasonPhrase());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, Object>> otro(Exception e) { return r(500, "INTERNAL_ERROR", "Error interno"); }
}
