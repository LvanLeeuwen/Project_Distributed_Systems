package ihome.server;

import ihome.proto.serverside.ServerProto;
import ihome.client.AliveCaller;
import ihome.proto.lightside.LightProto;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.userside.UserProto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;

import org.json.*;
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
	private Map<Integer, Boolean> uidalive = new HashMap<Integer, Boolean>();
	private int nextID = 0;
	private final int nr_types = 4;
	private String IPAddress;
	
	private Timer timer;
	private AliveResponder ar;
	
	public static final int check_alive_interval = 1000;
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public Controller() {}
	public Controller(String ip_address){
		timer = new Timer();
		ar = new AliveResponder(this);
		
		timer.scheduleAtFixedRate(ar, check_alive_interval, check_alive_interval);
		
		IPAddress = ip_address;
	}

	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	
	@Override
	public CharSequence connect(int device_type, CharSequence ip_address) throws AvroRemoteException {
		
		if(device_type < 0 || device_type >= nr_types)
		{
			return "{\"UID\" : NULL, \"Error\" : \"[Error] No such type defined.\"}";
		}
		
		try{
			uidmap.put(nextID, new Device(device_type, ip_address));
			if(device_type == 1)
				sensormap.put(nextID, new ArrayList<Float>());
			else if(device_type == 0){
				uidalive.put(nextID, true);
			}
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
					this), new InetSocketAddress(IPAddress, 6789));
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

	public CharSequence sendController() {
		try {
			// Create JSON object
			JSONObject json = new JSONObject();
			json.put("nextID", nextID);
			json.put("sensormap", sensormap);
			json.put("uidalive", uidalive);
			/*
			 * uidmap:
			 * each device has a type and an online value
			 */
			JSONObject jsonuidmap = new JSONObject();
			for (int id : uidmap.keySet()) {
				Device value = uidmap.get(id);
				JSONObject device = new JSONObject();
				device.put("type", value.type);
				device.put("is_online", value.is_online ? 1 : 0);
				device.put("ip_address", value.IPAddress.toString());
				jsonuidmap.put(String.valueOf(id), device);
			}
			json.put("uidmap", jsonuidmap);
			
			// Send json
			for (int id : uidmap.keySet()) {
				int type = uidmap.get(id).type;
				if (type == 0) {
					// Send me to user
				
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					CharSequence response = userproxy.update_controller(json.toString());
					return response;
				} else if (type == 2) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					CharSequence response = fridgeproxy.update_controller(json.toString());
					System.out.println(response);
					return response;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "sendController" + e.toString();
		}
		return "sendController finished";
	}

	public CharSequence updateController(CharSequence jsonController) {
		try {
			JSONObject json = new JSONObject(jsonController.toString());
			
			nextID = json.getInt("nextID");
			
			uidmap.clear();
			JSONObject jsonuidmap = json.getJSONObject("uidmap");
			Iterator<String> keys = jsonuidmap.keys();
			while (keys.hasNext()) {
				String nextKey = keys.next();
				int id = Integer.parseInt(nextKey);
				JSONObject devices = jsonuidmap.getJSONObject(nextKey);
				int type = devices.getInt("type");
				boolean online = devices.getInt("is_online") == 1 ? true : false;
				CharSequence ip_address = devices.getString("ip_address");
				uidmap.put(id, new Device(type, online, ip_address));
			}
			sensormap.clear();
			JSONObject jsonsensormap = json.getJSONObject("sensormap");
			keys = jsonsensormap.keys();
			while (keys.hasNext()) {
				String nextKey = keys.next();
				int id = Integer.parseInt(nextKey);
				ArrayList<Float> sensordata = new ArrayList<Float>();
				JSONArray jArray = jsonsensormap.getJSONArray(nextKey);
				if (jArray != null) {
					for (int i = 0; i < jArray.length(); i++) {
						sensordata.add((float)jArray.getDouble(i));
					}
				}
				sensormap.put(id, sensordata);
			}
			uidalive.clear();
			JSONObject jsonuidalive = json.getJSONObject("uidalive");
			keys = jsonuidalive.keys();
			while (keys.hasNext()) {
				String nextKey = keys.next();
				int id = Integer.parseInt(nextKey);
				boolean alive = jsonuidalive.getBoolean(nextKey);
				uidalive.put(id, alive);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "updateController" + e.toString();
		}		
		return "updateController Finished";
	}
	/**************************
	 ** DEVICE FUNCTIONALITY **
	 **************************/
	
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
	
	
	/**************************
	 ** SENSOR FUNCTIONALITY **
	 **************************/
	
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
		System.out.println("Currently in session("+ this.uidmap.size()+ "):");
		for(int id : uidmap.keySet())
		{
			System.out.println(id + " " +  Boolean.toString(uidmap.get(id).is_online));
		}
		System.out.print("\n");
	}


	
	/*************************
	 ** LIGHT FUNCTIONALITY **
	 *************************/
	
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
	
	
	/**************************
	 ** FRIDGE FUNCTIONALITY **
	 **************************/
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
	

	
	/*************************
	 ** ALIVE FUNCTIONALITY **
	 *************************/
	
	@Override
	public int i_am_alive(int uid) throws AvroRemoteException {
		this.uidalive.put(uid, true);
		return 0;
	}
	
	public void check_alive(){
		for(int i : this.uidalive.keySet()){
			this.uidmap.get(i).is_online = this.uidalive.get(i);
			this.uidalive.put(i, false);
		}
	}
	
	
	/*************************
	 ** MAIN FUNCTION       **
	 *************************/
	
	public static void main(String [] args){
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String server_ip = reader.nextLine();
		Controller controller = new Controller(server_ip);
		controller.runServer();

		while(true){
			System.out.println("What do you want to do?");
			System.out.println("1) Get in-session list");
			//System.out.println("2) Get state light");
			System.out.println("3) Switch state light");
			System.out.println("4) Get contents fridge");
			System.out.println("5) Get current en removed contents fridge");
			System.out.println("6) Get temperature list");
			System.out.println("7) send controller");
			
			int in = reader.nextInt();
			if(in == 1){
				
				controller.printInSession();
				/*try {
					controller.printInSession();
					//System.out.println(controller.get_all_devices());
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
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
			} else if (in == 6) {
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					System.out.println(controller.get_temperature_list(id, id));
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 7) {
				System.out.println(controller.sendController());
			} else {
				break;
			}
		}

		//controller.get_light_state(0);
		controller.stopServer();
	}

	
}
