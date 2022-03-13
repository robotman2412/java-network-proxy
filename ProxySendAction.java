package net.scheffers.robot.proxy;

import net.scheffers.robot.proxy.connection.OutgoingProxyClient;

public interface ProxySendAction {
	
	void onSendTo(Proxy<?> proxy, OutgoingProxyClient to);
	
}
