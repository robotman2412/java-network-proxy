package net.scheffers.robot.proxy.config;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ProxyConfig {
	
	public ProxyPeer[] peers;
	public String hostname;
	public int port;
	public String id;
	
	public transient File file;
	
	public ProxyConfig() {}
	
	public static ProxyConfig sampleConfig() {
		ProxyConfig cfg = new ProxyConfig();
		cfg.peers = new ProxyPeer[] {
				new ProxyPeer("peer", 1234)
		};
		cfg.hostname = "optional_hostname";
		cfg.port = 1234;
		cfg.id = "My fancy ID";
		return cfg;
	}
	
	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
	
	public static ProxyConfig fromString(String raw) {
		return new Gson().fromJson(raw, ProxyConfig.class);
	}
	
	public static ProxyConfig fromFile(File file) throws Exception {
		String str = Files.readString(file.toPath());
		ProxyConfig cfg = fromString(str);
		cfg.file = file;
		return cfg;
	}
	
	public void save() {
		if (file == null) {
			System.err.println("Cannot save proxy config: No file set.");
		} else {
			try {
				file.getParentFile().mkdirs();
				byte[] data = toString().getBytes(StandardCharsets.UTF_8);
				FileOutputStream output = new FileOutputStream(file);
				output.write(data);
				output.flush();
				output.close();
				System.out.println("Proxy config saved at " + file.getCanonicalPath());
			} catch (Exception e) {
				System.err.println("Error saving proxy config:");
				e.printStackTrace();
			}
		}
	}
	
}
