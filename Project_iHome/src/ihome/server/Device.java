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
	public CharSequence IPAddress;
	public int has_local_connect;
	
	public Device(int t, CharSequence i)
	{
		type = t;
		is_online = true;
		IPAddress = i;
		has_local_connect = -1;
	}
	
	public Device(int t, boolean o, CharSequence i) {
		type = t;
		is_online = o;
		IPAddress = i;
	}
	

}
