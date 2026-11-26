package cl.nanitomangas.clientes.service;

public class ApiException extends RuntimeException {
	public final int status;
	public final String error;

	public ApiException(int status, String error, String message) {
		super(message);
		this.status = status;
		this.error = error;
	}
}
