package net.scheffers.robot.proxy;

import net.scheffers.robot.proxy.config.*;
import net.scheffers.robot.proxy.connection.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 
 * Handles communication between both sides of a proxy.
 */
public class Proxy<MessageType> {
	
	// Lifespan of messages in milliseconds.
	// Messages older than this still in the queue will not be sent.
	public static long MESSAGE_LIFESPAN = 15000;
	// Connection timeout in milliseconds.
	// No valid ping response in time will cause a disconnection.
	public static long CONNECTION_TIMEOUT = 5000;
	// Connection timeout in milliseconds.
	// This is the time between attempts to open the socket.
	public static long RETRY_TIMEOUT = 5000;
	// Time after which to send a ping, in milliseconds before timeout.
	public static long PING_TIME = 1000;
	
	public boolean doExtensiveLogging;
	
	public String id;

	protected String host;
	protected int port;
	protected boolean isOpen;
	
	protected ServerSocket incomingServer;
	protected List<IncomingProxyClient> incomingClients;
	protected List<OutgoingProxyClient> outgoingClients;
	protected Queue<OutgoingProxyClient> toConnect;
	
	protected List<Consumer<MessageType>> handlers;
	
	protected Thread worker;
	protected Function<MessageType, String> serialiser;
	protected Function<String, MessageType> deserialiser;
	
	public File configFile;
	public ProxyConfig config;
	
	public Proxy(Function<String, MessageType> deserialiser) {
		this(MessageType::toString, deserialiser);
	}
	
	public Proxy(Function<MessageType, String> serialiser, Function<String, MessageType> deserialiser) {
		this.serialiser = serialiser;
		this.deserialiser = deserialiser;
		handlers = new ArrayList<>();
		incomingClients = new ArrayList<>();
		outgoingClients = new ArrayList<>();
		toConnect = new LinkedTransferQueue<>();
	}
	
	/** Loads and verifies the config file. */
	public void loadConfig(File configFile) {
		this.configFile = configFile;
		try {
			config = ProxyConfig.fromFile(configFile);
			id = config.id;
			open(config.hostname, config.port);
			for (ProxyPeer peer : config.peers) {
				connect(peer.hostname, peer.port);
			}
		} catch (Exception e) {
			e.printStackTrace();
			config = ProxyConfig.sampleConfig();
			config.file = configFile;
			config.save();
			System.err.println("Proxy is not configured: Please update hostnames and ports.");
			System.err.println("Sample config saved at " + configFile.getAbsolutePath());
		}
	}
	
	/** The worker thread which handles all communication. */
	protected void workerMethod() {
		System.out.println("Proxy worker started.");
		while (isOpen) {
			workerMethodLoop();
		}
		System.out.println("Proxy worker stopped.");
	}
	
	/** The LOOP of code that handles communications. */
	protected void workerMethodLoop() {
		if (incomingServer != null) {
			try {
				// Check for new incoming clients.
				Socket newSocket = incomingServer.accept();
				IncomingProxyClient client = new IncomingProxyClient(this, newSocket);
				
				// Add the new connection.
				incomingClients.add(client);
				if (doExtensiveLogging) {
					System.out.println("Accepted incoming connection from " + newSocket.getRemoteSocketAddress());
				}
			} catch (IOException ignored) {
				// There are no more clients.
			}
		} else {
			this.openSocket();
		}

		List<IncomingProxyClient> toRemove = new LinkedList<>();
		// Handle all incoming clients.
		// Incoming and outgoing clients will try again if the connection fails.
		for (IncomingProxyClient incoming : incomingClients) {
			try {
				incoming.handle();
			} catch (ProxyClosedException e) {
				if (doExtensiveLogging && incoming.getSocket() != null) {
					System.out.println("Incoming connection from " + incoming.getSocket().getRemoteSocketAddress() + " closed");
				}
				incoming.close();
				toRemove.add(incoming);
			} catch (IOException e) {
				if (doExtensiveLogging && incoming.getSocket() != null && isOpen) {
					System.err.println("Incoming connection from " + incoming.getSocket().getRemoteSocketAddress() + " error");
				}
				e.printStackTrace();
				incoming.close();
				toRemove.add(incoming);
			}
		}
		incomingClients.removeAll(toRemove);
		
		// Handle all outgoing clients.
		for (OutgoingProxyClient outgoing : outgoingClients) {
			outgoing.handle();
		}
		
		// Because of course the ConcurrentModificationException has to so this shit to me.
		// I don't care! I'll get to it in the next loop!
		while (!toConnect.isEmpty())
			outgoingClients.add(toConnect.remove());
	}
	
	/** Attempt to open the server socket. */
	protected void openSocket() {
		try {
			incomingServer = new ServerSocket();
			if (host == null) {
				incomingServer.bind(new InetSocketAddress(port));
			} else {
				incomingServer.bind(new InetSocketAddress(host, port));
			}
			incomingServer.setSoTimeout(1);
			if (doExtensiveLogging) {
				if (host == null) {
					System.out.println("Server socket accepting on port " + port);
				} else {
					System.out.println("Server socket accepting on " + host + ":" + port);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Open the proxy for incoming connections. */
	public void open(String host, int port) {
		// Enforce no double opening.
		if (isOpen) close();
		
		// Update some variables.
		this.host = host;
		this.port = port;
		incomingClients = new ArrayList<>();
		outgoingClients = new ArrayList<>();
		incomingServer = null;
		isOpen = true;
		
		// Start worker thread.
		// It will open the server socket automatically.
		worker = new Thread(this::workerMethod);
		worker.start();
		
		if (doExtensiveLogging) {
			if (host != null) {
				System.out.println("Opening proxy on " + host + ":" + port);
			} else {
				System.out.println("Opening proxy on port " + port);
			}
		}
	}

	/** Close all connections. */
	public void close() {
		if (doExtensiveLogging) {
			System.out.println("Closing proxy");
		}
		
		isOpen = false;
		
		try {
			if (incomingServer != null)
				incomingServer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (IncomingProxyClient incoming : incomingClients) {
			incoming.close();
		}
		for (OutgoingProxyClient outgoing : outgoingClients) {
			outgoing.close();
		}

		worker.interrupt();
	}
	
	/** Send event stub. */
	public void send(MessageType message) {
		if (doExtensiveLogging) {
			System.out.println("Send proxy:\n" + message);
		}
		for (OutgoingProxyClient outgoing : outgoingClients) {
			if (message instanceof ProxySendAction) {
				((ProxySendAction) message).onSendTo(this, outgoing);
			}
			outgoing.send(serialiser.apply(message));
		}
	}
	
	/** Receive event handler stub. */
	public void addRecvHandler(Consumer<MessageType> handler) {
		handlers.add(handler);
	}

	/** Handle a message being received. */
	public void handleMessage(IncomingProxyClient from, String rawData) {
		MessageType event = deserialiser.apply(rawData);
		for (Consumer<MessageType> handler : handlers) {
			handler.accept(event);
		}
	}
	
	/** Connect to a peer server. */
	public void connect(String host, int port) {
		if (doExtensiveLogging) {
			System.out.println("Connecting to peer " + host + ":" + port);
		}
		toConnect.add(new OutgoingProxyClient(this, host, port));
	}
	
	/** Whether the proxy is open for connections. */
	public boolean isOpen() {
		return isOpen;
	}
	
}
