package ihome.client;

import java.util.TimerTask;

public class AliveCaller extends TimerTask{
	
	private User u;
	
	public AliveCaller(User user){
		this.u = user;
	}
	
	
	@Override
	public void run(){
		u.send_alive();
	}

}
