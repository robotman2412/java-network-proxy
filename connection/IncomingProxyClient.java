package net.scheffers.robot.proxy.connection;

import net.scheffers.robot.proxy.Proxy;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class IncomingProxyClient extends ProxyClient {
	
	protected boolean isReading;
	protected ByteArrayOutputStream inputBuffer;
	
	public IncomingProxyClient(Proxy<?> parent, Socket socket) throws IOException {
		this.parent = parent;
		setSocket(socket, System.currentTimeMillis());
		inputBuffer = new ByteArrayOutputStream();
	}
	
	public void handle() throws IOException {
		// Send pings.
		long now = System.currentTimeMillis();
		sendPings(now);
		
		if (isReading) {
			int available = input.available();
			// Read until the byte 0x03 is reached.
			while (available > 0) {
				int val = input.read();
				if (val == 5 && output != null) {
					// Respond to the ping.
					output.write(6);
					output.flush();
				} else if (val == 6) {
					// Our ping got a response.
					lastPingResp = now;
				} else if (val == 4) {
					// The connection is closed.
					throw new ProxyClosedException();
				} else if (val == 2) {
					// Well oops.
					// Empty the buffer.
					inputBuffer = new ByteArrayOutputStream();
				} else if (val == 3) {
					// Trigger the receive logic.
					isReading = false;
					handleRawMessage(inputBuffer.toString(StandardCharsets.UTF_8));
					break;
				} else {
					// Buffer the incoming data.
					inputBuffer.write((byte) val);
				}
				available = input.available();
			}
		} else {
			int available = input.available();
			// Skip until the byte 0x02 is reached.
			while (available > 0) {
				int val = input.read();
				if (val == 5 && output != null) {
					// Respond to the ping.
					output.write(6);
					output.flush();
				} else if (val == 6) {
					// Our ping got a response.
					lastPingResp = now;
				} else if (val == 4) {
					// The connection is closed.
					throw new ProxyClosedException();
				} else if (val == 2) {
					// Trigger the reading logic.
					isReading = true;
					inputBuffer = new ByteArrayOutputStream();
					break;
				}
				available = input.available();
			}
		}
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	protected void handleRawMessage(String raw) {
		if (parent.doExtensiveLogging)
			System.out.println("Incoming message:\n" + raw);
		parent.handleMessage(this, raw);
	}
	
	public void close() {
		try {
			output.write(4);
			output.flush();
			socket.close();
		} catch (IOException ignored) {}
	}
	
}
