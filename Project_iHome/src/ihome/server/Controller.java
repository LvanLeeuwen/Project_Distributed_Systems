package ihome.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;

import ihome.proto.serverside.ServerProto;


public class Controller implements ServerProto 
{
	
	private Map<Integer, Device> uidmap = new HashMap<Integer, Device>();
	private Map<Integer, ArrayList<Float>> sensormap = new HashMap<Integer, ArrayList<Float>>();
	private int nextID = 0;
	private final int nr_types = 4;

	@Override
	public CharSequence connect(int device_type) throws AvroRemoteException {
		
		if(device_type < 0 || device_type >= nr_types)
		{
			return "{\"UID\" : NULL, \"Error\" : \"[Error] No such type defined.\"}";
		}
		
		try{
			uidmap.put(nextID, new Device(device_type));
			if(device_type == 1)
				sensormap.put(nextID, new ArrayList<Float>());
			return "{\"UID\" : \""+ (nextID++) + "\", \"Error\" : NULL}";
		}catch(Exception e){
			return "{\"UID\" : NULL, \"Error\" : \"[Error] " + e.getMessage();
		}
	}

	@Override
	public CharSequence disconnect(int uid) throws AvroRemoteException {
		
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
	public CharSequence update_temperature(int uid, float value) throws AvroRemoteException {
		if(!uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		else if(!sensormap.containsKey(uid)){
			return "{\"Error\" : \"[Error] Device with uid " + uid +  " is not heat sensor.\"}";
		}
		try{
			this.sensormap.get(uid).add(value);
			return "{\"Error\" : NULL}";

		}catch (Exception e){

		}
		return null;
	}

	

	@Override
	public CharSequence get_temperature_list(int uid, int sensor_id) throws AvroRemoteException {
		if(!uidmap.containsKey(sensor_id)) {
			return "{\"Error\" : \"[Error] sensor_id not found in current session.\"}";
		}
		else if(!uidmap.containsKey(uid)){
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		else if(!sensormap.containsKey(sensor_id)){
			return "{\"Error\" : \"[Error] Device with uid " + uid +  " is not heat sensor.\"}";
		}
		try{
			return this.sensormap.get(sensor_id).toString();

		}catch (Exception e){

		}
		return null;
	}

	@Override
	public CharSequence get_temperature_current(int uid) throws AvroRemoteException {
		if(!uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		
		try{
			double mean = 0.0;
			int n = 0;
			for(ArrayList<Float>  c : this.sensormap.values()){
				n++;
				mean += c.get(c.size() - 1);
			}
			return Double.toString(mean/n);

		}catch (Exception e){

		}
		return null;
	}
	
	
	public void printInSession(){
		System.out.println("Currently in session:");
		for(int id : uidmap.keySet())
		{
			System.out.print(id + " ");
		}
		System.out.print("\n");
	}

	public static void runServer(){
		Server server = null;
		try
		{
			server = new SaslSocketServer(new SpecificResponder(ServerProto.class,
					new Controller()), new InetSocketAddress(6789));
		}catch (IOException e){
			System.err.println("[error] failed to start server");
			e.printStackTrace(System.err);
			System.exit(1);

		}
		server.start();
		try{
			server.join();

		}catch(InterruptedException e){}
	}
	
	public static void main(String [] args){
		//Controller.runServer();
		
		Controller c = new Controller();
		try {
			System.out.println(c.connect(1));
			System.out.println(c.connect(1));
			System.out.println(c.connect(0));
			
			System.out.println(c.update_temperature(0, 0.0f));
			System.out.println(c.update_temperature(0, 5.0f));
			System.out.println(c.update_temperature(1, 1.0f));
			System.out.println(c.update_temperature(1, 5.0f));
			System.out.println(c.update_temperature(1, 3.0f));
			
			System.out.println(c.get_temperature_list(3, 1));
			System.out.println(c.get_temperature_current(2));
			
			
		} catch (AvroRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
