package ihome.server;

import ihome.proto.serverside.ServerProto;
import ihome.proto.lightside.LightProto;
import ihome.proto.fridgeside.FridgeProto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;


public class Controller implements ServerProto 
{
	private static Server server = null;
	private Map<Integer, Device> uidmap = new HashMap<Integer, Device>();
	private Map<Integer, ArrayList<Float>> sensormap = new HashMap<Integer, ArrayList<Float>>();
	private int nextID = 0;
	private final int nr_types = 4;

	
	/* Server related */
	
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
			System.out.println("Device connected with id " + nextID);
			return "{\"UID\" : \""+ (nextID++) + "\", \"Error\" : NULL}";
		}catch(Exception e){
			return "{\"UID\" : NULL, \"Error\" : \"[Error] " + e.getMessage();
		}
	}

	@Override
	public CharSequence disconnect(int uid) throws AvroRemoteException {
		System.out.println("disconnect");
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
	
	public void runServer(){
		try
		{
			server = new SaslSocketServer(new SpecificResponder(ServerProto.class,
					this), new InetSocketAddress(6789));
		}catch (IOException e){
			System.err.println("[error] failed to start server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
		server.start();
	}
	
	public void stopServer() {
		try {
			server.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/* All devices */
	
	@Override
	public CharSequence get_all_devices() throws AvroRemoteException {
		CharSequence inSession = "Currently in session("+ this.uidmap.size()+ "):\n";
		for(int id : uidmap.keySet())
		{
			int type = uidmap.get(id).type;
			inSession = inSession.toString() + id + " " + type + "\n";
		}
		inSession = inSession.toString() + "\n";
		return inSession;
	}
	
	
	/* Temperature sensor */
	
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

	
	/* Light */
	
	@Override
	public CharSequence get_lights_state() throws AvroRemoteException {
		try {
			CharSequence lights = "";
			for (Map.Entry<Integer, Device> entry : uidmap.entrySet()) {
				Integer key = entry.getKey();
				Device device = entry.getValue();
				if (device.type == 3) {
					Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(6790+key));
					LightProto proxy = SpecificRequestor.getClient(LightProto.class, trans);
					CharSequence state = proxy.send_state();
					lights = lights.toString() + key + " " + state + "\n";
				}
			}
			return lights;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	public CharSequence switch_state_light(int uid) throws AvroRemoteException {
		Device light = uidmap.get(uid);
		if (light.type != 3) {
			return "{\"Switched\" : false, \"Error\" : \"[Error] light_id not found in current session.\"}";
		}
		try {
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(6790+uid));
			LightProto proxy = SpecificRequestor.getClient(LightProto.class, trans);
			CharSequence state = proxy.switch_state();
			return "{\"Switched\" : true, \"Error\" : NULL}";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	
	/* Fridge */
	@Override
	public CharSequence get_fridge_contents(int uid) throws AvroRemoteException {
		Device fridge = uidmap.get(uid);
		if (fridge.type != 2) {
			return "{\"Contents\" : NULL, \"Error\" : \"[Error] fridge_id not found in current session.\"}";
		}
		try {
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(6790+uid));
			FridgeProto proxy = SpecificRequestor.getClient(FridgeProto.class, trans);
			CharSequence contents = proxy.send_current_items();
			return "{\"Contents\" : contents, \"Error\" : NULL}";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public void get_all_fridge_contents(int uid) {
		Device fridge = uidmap.get(uid);
		if (fridge.type != 2) {
			// TODO error
			return;
		}
		try {
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(6790+uid));
			FridgeProto proxy = SpecificRequestor.getClient(FridgeProto.class, trans);
			CharSequence contents = proxy.send_all_items();
			System.out.println(contents);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/* Main */
	public static void main(String [] args){
		Controller controller = new Controller();
		controller.runServer();

		while(true){
			Scanner reader = new Scanner(System.in);
			System.out.println("What do you want to do?");
			System.out.println("1) Get in-session list");
			//System.out.println("2) Get state light");
			System.out.println("3) Switch state light");
			System.out.println("4) Get contents fridge");
			System.out.println("5) Get current en removed contents fridge");
			
			int in = reader.nextInt();
			if(in == 1){
				try {
					System.out.println(controller.get_all_devices());
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} /*else if(in ==2){
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					controller.get_light_state(id);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} */else if(in ==3){
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					controller.switch_state_light(id);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 4) {
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					controller.get_fridge_contents(id);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 5) {
				System.out.println("Give id:");
				int id = reader.nextInt();
				controller.get_all_fridge_contents(id);
			} else {
				break;
			}
		}

		//controller.get_light_state(0);
		controller.stopServer();
	}
}
