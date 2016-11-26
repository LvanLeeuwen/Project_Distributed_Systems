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
	// Controller variables
	private static Server server = null;
	private Map<Integer, Device> uidmap = new HashMap<Integer, Device>();
	private Map<Integer, ArrayList<Float>> sensormap = new HashMap<Integer, ArrayList<Float>>();
	int sizeSensorMap = 10;
	private Map<Integer, Boolean> uidalive = new HashMap<Integer, Boolean>();
	private Map<Integer, Boolean> fridgeAlive = new HashMap<Integer, Boolean>();
	private int nextID = 0;
	private final int nr_types = 4;
	private String IPAddress;
	
	// Alive variables
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
			this.sendController(nextID);
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
			this.sendController();
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

	@Override
	public int sendController() throws AvroRemoteException {
		try {
			// Create JSON object
			JSONObject json = new JSONObject();
			json.put("nextID", nextID);
			json.put("sensormap", sensormap);
			json.put("uidalive", uidalive);
			/*
			 * uidmap:
			 * each device has:
			 * 		type 
			 * 		online value
			 * 		IPAddress
			 * 		has_local_connect
			 */
			JSONObject jsonuidmap = new JSONObject();
			for (int id : uidmap.keySet()) {
				Device value = uidmap.get(id);
				JSONObject device = new JSONObject();
				device.put("type", value.type);
				device.put("is_online", value.is_online ? 1 : 0);
				device.put("ip_address", value.IPAddress.toString());
				device.put("has_local_connect", value.has_local_connect);
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
				} else if (type == 2) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					CharSequence response = fridgeproxy.update_controller(json.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
		return 0;
	}

	public void sendController(int uid) {
		try {
			// Create JSON object
			JSONObject json = new JSONObject();
			json.put("nextID", nextID);
			json.put("sensormap", sensormap);
			json.put("uidalive", uidalive);
			/*
			 * uidmap:
			 * each device has:
			 * 		type 
			 * 		online value
			 * 		IPAddress
			 * 		has_local_connect
			 */
			JSONObject jsonuidmap = new JSONObject();
			for (int id : uidmap.keySet()) {
				Device value = uidmap.get(id);
				JSONObject device = new JSONObject();
				device.put("type", value.type);
				device.put("is_online", value.is_online ? 1 : 0);
				device.put("ip_address", value.IPAddress.toString());
				device.put("has_local_connect", value.has_local_connect);
				jsonuidmap.put(String.valueOf(id), device);
			}
			json.put("uidmap", jsonuidmap);
			
			// Send json
			for (int id : uidmap.keySet()) {
				// Sla opgegeven uid over
				if (id == uid) continue;
				
				int type = uidmap.get(id).type;
				if (type == 0) {
					// Send me to user
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					CharSequence response = userproxy.update_controller(json.toString());
					return;
				} else if (type == 2) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					CharSequence response = fridgeproxy.update_controller(json.toString());
					return;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		return;
	}

	public CharSequence updateController(CharSequence jsonController) {
		try {
			JSONObject json = new JSONObject(jsonController.toString());
			
			nextID = json.getInt("nextID");
			
			/*
			 * uidmap:
			 * each device has:
			 * 		type 
			 * 		online value
			 * 		IPAddress
			 * 		has_local_connect
			 */
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
				int has_local_connect = devices.getInt("has_local_connect");
				uidmap.put(id, new Device(type, online, ip_address, has_local_connect));
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
		return "";
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
			while (this.sensormap.get(uid).size() > sizeSensorMap) {
				this.sensormap.get(uid).remove(0);
			}
			this.sendController();
			return "{\"Error\" : NULL}";

		}catch (Exception e){

		}
		return null;
	}

	@Override
	public CharSequence get_temperature_list() throws AvroRemoteException {
		try{
			ArrayList<Float> result = new ArrayList<Float>();
			for (int size = sizeSensorMap; size > 0; size--) {
				double mean = 0.0;
				int n = 0;
				for (ArrayList<Float> c : this.sensormap.values()) {
					if (c.size() >= size) {
						n++;
						mean += c.get(c.size() - size);
					}
				}
				if (n != 0) {
					result.add((float) (mean / n));
				}
			}
			
			return result.toString();

		}catch (Exception e){

		}
		return null;
	}

	@Override
	public CharSequence get_temperature_current() throws AvroRemoteException {
		// When there are multiple sensors, return average of last sent temperatures.
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
			System.out.println(id + " " +  Boolean.toString(uidmap.get(id).is_online) + " " + uidmap.get(id).type +" "+ uidmap.get(id).has_local_connect);
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
			this.sendController();
			return "{\"Switched\" : true, \"Error\" : NULL}";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	
	/*************************
	 ** ALIVE FUNCTIONALITY **
	 *************************/
	
	@Override
	public int i_am_alive(int uid) throws AvroRemoteException {
		if (uidmap.get(uid).type == 0) {
			this.uidalive.put(uid, true);
		} else if (uidmap.get(uid).type == 2) {
			this.fridgeAlive.put(uid, true);
		}
		return 0;
	}
	
	public void check_alive(){
		// Check alive users
		for(int i : this.uidalive.keySet()){
			this.uidmap.get(i).is_online = this.uidalive.get(i);
			this.uidalive.put(i, false);
		}
		// Check alive fridges
		for (int i : this.fridgeAlive.keySet()) {
			this.uidmap.get(i).is_online = this.fridgeAlive.get(i);
			this.fridgeAlive.put(i, false);
		}
		
		// Check if a user is connected to a fridge
		for(int j : this.uidmap.keySet()){
			int c_id = this.uidmap.get(j).has_local_connect;
			if(c_id >= 0){
				if(!this.uidmap.get(c_id).is_online ){
					this.uidmap.get(j).has_local_connect = -1;
				}
					
			}
		}
	}
	
	

	/*********************
	 ** FRIDGE FUNTIONS **
	 *********************/
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
			return "{\"Contents\" : " + contents + ", \"Error\" : NULL}";
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
	
	@Override
	public CharSequence get_fridge_port(int uid, int fridgeid) throws AvroRemoteException {
		if(!this.uidmap.containsKey(fridgeid))
			return "{\"socket\" : NULL}";
		
		if(!this.uidmap.get(fridgeid).is_online){
			return "{\"socket\" : NULL, \"Error\" : \"[Error] Fridge is offline.\"}";
		}
		
		if(this.uidmap.get(fridgeid).has_local_connect >= 0)
			return "{\"socket\" : NULL, \"Error\" : \"[Error] fridge already in use.\"}";
				
		
		if(this.uidmap.get(fridgeid).type == 2){
			this.uidmap.get(fridgeid).has_local_connect = uid;
			return "{\"socket\" : " + (fridgeid + 6790) + "}";
		}
		else{
			return "{\"socket\" : NULL}";
		}
	}

	@Override
	public int release_fridge(int uid) throws AvroRemoteException {
		this.uidmap.get(uid).has_local_connect = -1;
		this.sendController();
		return 0;
	}

	@Override
	public int report_offline(int uid) throws AvroRemoteException {
		this.uidmap.get(uid).is_online = false;
		this.sendController();
		return 0;
	}

	@Override
	public int notify_empty_fridge(int uid) throws AvroRemoteException {
		try{
			if(this.uidmap.get(uid).type == 2){
				for (int id : uidmap.keySet()) {
					int type = uidmap.get(id).type;
					if (type == 0) {
						// Send me to user
						Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
						UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
						int response = userproxy.notify_empty_fridge(uid);
						
					} 
				}
			}
				
		}catch(Exception e){
			
		}
		return 0;
	}
	
	/*******************
	 * LEADER ELECTION *
	 *******************/
	
	public Map<Integer, CharSequence> getPossibleParticipants(){
		Map<Integer, CharSequence>out = new HashMap<Integer, CharSequence>();
		for(int key : uidmap.keySet()){
			if((uidmap.get(key).type == 0 || uidmap.get(key).type == 2)){
				out.put(key, uidmap.get(key).IPAddress);
			}
		}
		return out;
	}
	
	public Map<Integer, Device> getUidmap(){
		return this.uidmap;
	}
	
	/**********************
	 * USER ENTERS/LEAVES *
	 **********************/
	@Override
	public CharSequence user_enters(int uid) throws AvroRemoteException {
		if (!this.uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		try {
			this.uidalive.put(uid, true);
			this.uidmap.get(uid).is_online = true;
			this.sendController();
			// Send message to all other users
			for (int id : uidmap.keySet()) {
				if (id != uid && uidmap.get(id).type == 0 && uidmap.get(id).is_online) {
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					int response = userproxy.notify_user_enters(uid);
				}
			}
			return "{\"Error\" : NULL}";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	@Override
	public CharSequence user_leaves(int uid) throws AvroRemoteException {
		if (!this.uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		try {
			this.uidalive.put(uid, false);
			this.uidmap.get(uid).is_online = false;
			this.sendController();
			// Send message to all other users
			// Send message to all other users
			for (int id : uidmap.keySet()) {
				if (id != uid && uidmap.get(id).type == 0 && uidmap.get(id).is_online) {
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					int response = userproxy.notify_user_leaves(uid);
				}
			}
			return "{\"Error\" : NULL}";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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
			System.out.println("7) Get current temperature");
			System.out.println("8) send controller");
			
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
				System.out.println(controller.sensormap.toString());
			} else if (in == 7) {
				try {
					System.out.println(controller.get_temperature_current());
				} catch (AvroRemoteException e) {
					e.printStackTrace();
				}
			} else if (in == 8) {
				try {
					controller.sendController();
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				break;
			}
		}

		//controller.get_light_state(0);
		controller.stopServer();
	}
	
}
