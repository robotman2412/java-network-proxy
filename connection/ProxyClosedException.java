package net.scheffers.robot.proxy.connection;

import java.io.IOException;

public class ProxyClosedException extends IOException {
	
	public ProxyClosedException() {
		super("Port closed");
	}
	
	public ProxyClosedException(Throwable cause) {
		super("Port closed", cause);
	}
	
	public ProxyClosedException(String message) {
		super("Port closed: " + message);
	}
	
	public ProxyClosedException(String message, Throwable cause) {
		super("Port closed: " + message, cause);
	}
	
}
