package net.scheffers.robot.proxy.connection;

import net.scheffers.robot.proxy.Proxy;

import java.io.*;
import java.net.Socket;

public class IncomingProxyClient extends ProxyClient {
	
	protected boolean isReading;
	protected StringBuffer inputBuffer;
	
	public IncomingProxyClient(Proxy parent, Socket socket) throws IOException {
		this.parent = parent;
		setSocket(socket, System.currentTimeMillis());
		inputBuffer = new StringBuffer();
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
					inputBuffer = new StringBuffer();
				} else if (val == 3) {
					// Trigger the receive logic.
					isReading = false;
					handleRawMessage(inputBuffer.toString());
					break;
				} else {
					// Buffer the incoming data.
					inputBuffer.append((char) val);
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
					inputBuffer = new StringBuffer();
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
