package ihome.server;

import java.util.HashMap;
import java.util.Map;

import org.apache.avro.AvroRemoteException;

import ihome.proto.serverside.ServerProto;


public class Controller implements ServerProto 
{
	
	private Map<String, Device> uidmap = new HashMap<String, Device>();
	private int nextID = 0;
	private final int nr_types = 4;

	@Override
	public CharSequence connect(int device_type) throws AvroRemoteException {
		
		if(device_type < 0 || device_type >= nr_types)
		{
			return "{\"UID\" : NULL, \"Error\" : \"[Error] No such type defined.\"}";
		}
		
		try{
			uidmap.put(Integer.toString(nextID), new Device(device_type));
			return "{\"UID\" : \""+ (nextID++) + "\", \"Error\" : NULL}";
		}catch(Exception e){
			return "{\"UID\" : NULL, \"Error\" : \"[Error] " + e.getMessage();
		}
	}

	@Override
	public CharSequence disconnect(CharSequence uid) throws AvroRemoteException {
		
		if(!uidmap.containsKey(uid))
		{
			return "{\"Error\" : \"[Error] uid not found in current session, maybe you are already disconnected?\"}";
		}
		try{
			uidmap.remove(uid);
			return "{\"Error\" : NULL}";
		}catch(Exception e){
			return "{\"Error\" : \"[Error] " + e.getMessage();
		}
		
	}

	@Override
	public CharSequence update_temperature(CharSequence uid) throws AvroRemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CharSequence get_temperature_list(CharSequence uid, CharSequence sensor_id) throws AvroRemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CharSequence get_temperature_current(CharSequence uid) throws AvroRemoteException {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	
	
	public static void main(String [] args){
		System.out.println("Hello world" );
		
		Controller c = new Controller();
		try {
			System.out.println(c.connect(0));
			System.out.println(c.connect(1));
			System.out.println(c.connect(2));
			System.out.println(c.connect(4));
			System.out.println(c.connect(-1));
			System.out.println(c.connect(3));
			
		} catch (AvroRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	

}
