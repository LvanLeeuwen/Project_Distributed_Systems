package ihome.client;

import java.util.TimerTask;

public class AliveCaller extends TimerTask{
	
	private User u;
	private Fridge f;
	private Light l;
	private TemperatureSensor ts;
	
	public AliveCaller(User user){
		this.u = user;
	}
	
	public AliveCaller(Fridge fridge){
		this.f = fridge;
	}
	
	public AliveCaller(Light light){
		this.l = light;
	}
	
	public AliveCaller(TemperatureSensor temp){
		this.ts = temp;
	}
	
	public void update(User user){
		this.u = user;
	}
	
	public void update(Fridge fridge){
		this.f = fridge;
	}
	
	public void update(Light light){
		this.l = light;
	}
	
	public void update(TemperatureSensor temp){
		this.ts = temp;
	}
	
	@Override
	public void run(){
		if (u != null) {
			u.send_alive();
		}
		else if (f != null) {
			f.send_alive();
		}
		else if (l != null){
			l.send_alive();
		}
		else if (ts != null){
			ts.send_alive();
		}
	}

}
