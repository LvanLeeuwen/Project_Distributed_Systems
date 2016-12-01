package ihome.server;

import java.util.TimerTask;

public class PingServer extends TimerTask {
	Controller controller;
	
	public PingServer(Controller c) {
		this.controller = c;
	}
	
	@Override 
	public void run() {
		controller.pingServer();
	}
}
