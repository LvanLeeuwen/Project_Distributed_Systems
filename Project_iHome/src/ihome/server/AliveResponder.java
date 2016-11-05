package ihome.server;

import java.util.TimerTask;

public class AliveResponder  extends TimerTask {
	Controller controller;

	public AliveResponder(Controller c){
		this.controller = c;
	}
	
	@Override
	public void run() {
		controller.check_alive();
	}

}
