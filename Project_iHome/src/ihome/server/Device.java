package ihome.server;

public class Device {
	
	/*
	 * Device type:
	 * 0 = user
	 * 1 = sensor
	 * 2 = fridge
	 * 3 = light
	 */
	public int type;
	public boolean is_online;
	
	public Device(int t)
	{
		type = t;
		is_online = true;
	}
	
	public Device(int t, boolean o) {
		type = t;
		is_online = o;
	}
	

}
