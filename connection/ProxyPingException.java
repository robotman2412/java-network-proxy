package net.scheffers.robot.proxy.connection;

public class ProxyPingException extends ProxyClosedException {

	public ProxyPingException() {
		super("No response to ping");
	}

	public ProxyPingException(Throwable cause) {
		super("No response to ping", cause);
	}

	public ProxyPingException(String message) {
		super("No response to ping: " + message);
	}

	public ProxyPingException(String message, Throwable cause) {
		super("No response to ping: " + message, cause);
	}

}
