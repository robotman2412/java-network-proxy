package net.scheffers.robot.proxy.connection;

import net.scheffers.robot.proxy.Proxy;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;

public class OutgoingProxyClient extends ProxyClient {
	
	protected String host;
	protected int port;
	
	protected long lastAttempt;
	
	protected Queue<TimestampedMessage> sendQueue;
	
	public OutgoingProxyClient(Proxy parent, String host, int port) {
		this.parent = parent;
		this.host = host;
		this.port = port;
		socket = null;
		output = null;
		lastAttempt = 0;
		lastPing = 0;
		lastPingResp = 0;
		sendQueue = new LinkedTransferQueue<>();
	}
	
	/** Attempt to open the client socket. */
	protected void openSocket() {
		long now = System.currentTimeMillis();
		if (lastAttempt + Proxy.RETRY_TIMEOUT < now) {
			lastAttempt = now;
			try {
				socket = new Socket(host, port);
				setSocket(socket, now);
			} catch (IOException e) {
				socket = null;
			}
			if (socket != null && parent.doExtensiveLogging) {
				System.out.println("Connected to peer " + host + ":" + port);
			}
		}
	}
	
	/** Close the connection. */
	public void close() {
		try {
			if (socket != null) {
				output.write(4);
				output.flush();
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Handle and keep alive the connection. */
	public void handle() {
		if (socket == null) {
			// Try to open the socket.
			openSocket();
		} else {
			long now = System.currentTimeMillis();
			
			// Send pings.
			try {
				sendPings(now);
			} catch (ProxyClosedException e) {
				System.err.println("Error in outgoing connection to " + host + ":" + port + ": " + e.getMessage());
				if (e.getCause() != e && e.getCause() != null) {
					e.printStackTrace();
				}
				socket = null;
				input  = null;
				output = null;
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Check for ping response.
			try {
				if (input != null && input.available() > 0) {
					int val = input.read();
					if (val == 6) {
						// Valid ping response.
						lastPingResp = now;
					} else if (val == 5) {
						// Ping request.
						output.write(6);
						output.flush();
					} else if (val == 4) {
						// Closing of the connection.
						if (parent.doExtensiveLogging) {
							System.out.println("Outgoing connection to " + host + ":" + port + " closed");
						}
						socket = null;
						input  = null;
						output = null;
						return;
					}
				}
			} catch (IOException e) {
				if (parent.isOpen()) {
					System.err.println("Error in outgoing connection to " + host + ":" + port);
				}
				e.printStackTrace();
				try {
					socket.close();
				} catch (Exception ignored) {}
				socket = null;
				input  = null;
				output = null;
			}
			
			// Send messages from the queue.
			long limit = now - Proxy.MESSAGE_LIFESPAN;
			int dropped = 0;
			while (!sendQueue.isEmpty()) {
				TimestampedMessage raw = sendQueue.peek();
				if (raw.time < limit) {
					// Drop messages too old.
					dropped ++;
				} else if (sendRawMessage(raw.text)) {
					// Remove messages successfully sent.
					sendQueue.remove();
				} else {
					// Stop on failure.
					break;
				}
			}
			
			// Notify of dropped messages.
			if (dropped > 0)
				System.err.println("Dropped " + dropped + " old message" + (dropped > 1 ? "s" : "") + " for " + host + ":" + port);
		}
	}
	
	/** Add a string object to the queue for sending. */
	public void send(String toSend) {
		sendQueue.add(new TimestampedMessage(toSend, System.currentTimeMillis()));
	}
	
	/** Simple method for sending a raw string message. */
	protected boolean sendRawMessage(String raw) {
		try {
			output.write(2);
			output.write(raw.getBytes(StandardCharsets.UTF_8));
			output.write(3);
			return true;
		} catch (IOException e) {
			System.err.println("Error in outgoing connection to " + host + ":" + port);
			e.printStackTrace();
			try {
				socket.close();
			} catch (Exception ignored) {}
			socket = null;
			input  = null;
			output = null;
			return false;
		}
	}
	
	protected static class TimestampedMessage {
		
		public String text;
		public long   time;
		
		public TimestampedMessage(String text, long time) {
			this.text = text;
			this.time = time;
		}
		
	}
	
}
