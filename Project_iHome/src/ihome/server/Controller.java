package ihome.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.AvroRemoteException;

import ihome.proto.serverside.ServerProto;


public class Controller implements ServerProto 
{
	
	private Map<String, Device> uidmap = new HashMap<String, Device>();
	private Map<String, ArrayList<Float>> sensormap = new HashMap<String, ArrayList<Float>>();
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
			if(device_type == 1)
				sensormap.put(Integer.toString(nextID), new ArrayList<Float>());
			return "{\"UID\" : \""+ (nextID++) + "\", \"Error\" : NULL}";
		}catch(Exception e){
			return "{\"UID\" : NULL, \"Error\" : \"[Error] " + e.getMessage();
		}
	}

	@Override
	public CharSequence disconnect(CharSequence uid) throws AvroRemoteException {
		
		if(!uidmap.containsKey(uid))
		{
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		try{
			uidmap.remove(uid);
			return "{\"Error\" : NULL}";
		}catch(Exception e){
			return "{\"Error\" : \"[Error] " + e.getMessage();
		}
		
	}
	
	@Override
	public CharSequence update_temperature(CharSequence uid, float value) throws AvroRemoteException {
		if(!uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		else if(!sensormap.containsKey(uid)){
			return "{\"Error\" : \"[Error] Device with uid " + uid +  " is not heat sensor.\"}";
		}
		try{

		}catch (Exception e){

		}
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
	
	
	public void printInSession(){
		System.out.println("Currently in session:");
		for(String id : uidmap.keySet())
		{
			System.out.print(id + " ");
		}
		System.out.print("\n");
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
			System.out.println(c.disconnect("3"));
			c.printInSession();


		} catch (AvroRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	

	

}
