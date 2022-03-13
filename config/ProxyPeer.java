package net.scheffers.robot.proxy.config;

import com.google.gson.Gson;

public class ProxyPeer {
	
	public String hostname;
	public int port;
	
	public ProxyPeer() {}

	public ProxyPeer(String hostname, int port) {
		this.hostname = hostname;
		this.port = port;
	}

	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
	
	public static ProxyPeer fromString(String raw) {
		return new Gson().fromJson(raw, ProxyPeer.class);
	}
	
}
