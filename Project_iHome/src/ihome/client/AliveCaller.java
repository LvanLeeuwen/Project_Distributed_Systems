package ihome.client;

import java.util.TimerTask;

public class AliveCaller extends TimerTask{
	
	private User u;
	private Fridge f;
	
	public AliveCaller(User user){
		this.u = user;
	}
	
	public AliveCaller(Fridge fridge){
		this.f = fridge;
	}
	
	public void update(User user){
		this.u = user;
	}
	
	public void update(Fridge fridge){
		this.f = fridge;
	}
	
	@Override
	public void run(){
		if (u != null) {
			u.send_alive();
		}
		if (f != null) {
			f.send_alive();
		}
	}

}
