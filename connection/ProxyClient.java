package net.scheffers.robot.proxy.connection;

import net.scheffers.robot.proxy.Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ProxyClient {
	
	public Proxy parent;
	
	protected Socket socket;
	protected InputStream input;
	protected OutputStream output;
	
	protected long lastPing;
	protected long lastPingResp;
	
	protected ProxyClient() {}
	
	/** Handles outgoing pings.
	 *  @throws ProxyPingException when the timeout is reached
	 */
	protected void sendPings(long currentTime) throws IOException {
		// Check for timeout.
		if (lastPingResp + Proxy.CONNECTION_TIMEOUT < currentTime) {
			// Timeout reached.
			try {
				socket.close();
				throw new ProxyPingException();
			} catch (Exception e) {
				throw new ProxyPingException(e);
			}
		}

		// Send ping.
		if (output != null && lastPing + Proxy.CONNECTION_TIMEOUT - Proxy.PING_TIME < currentTime) {
			lastPing = currentTime;
			output.write(5);
			output.flush();
		}
	}
	
	protected void setSocket(Socket socket, long time) throws IOException {
		this.socket = socket;
		socket.setKeepAlive(true);
		socket.setSoTimeout(100);
		input  = socket.getInputStream();
		output = socket.getOutputStream();
		lastPing = time;
		lastPingResp = time;
	}
	
}
