package ihome.server;

import ihome.proto.sensorside.SensorProto;
import ihome.proto.serverside.ServerProto;
import ihome.proto.lightside.LightProto;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.userside.UserProto;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
	private boolean isOriginal;
	
	// Alive variables
	private Timer timer;
	private AliveResponder ar;
	public static final int check_alive_interval = 1000;
	
	// Ping server variables
	private Timer pingTimer;
	private PingServer ps;
	private CharSequence originalIPAddress;
	
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public Controller() {}
	public Controller(String ip_address, boolean original){
		IPAddress = ip_address;
		isOriginal = original;
		if (isOriginal) {
			originalIPAddress = ip_address;
		}
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
	
	public void runServer() {
		try
		{
			timer = new Timer();
			ar = new AliveResponder(this);
			timer.scheduleAtFixedRate(ar, check_alive_interval, check_alive_interval);
			if (isOriginal) {
				server = new SaslSocketServer(new SpecificResponder(ServerProto.class,
						this), new InetSocketAddress(IPAddress, 6789));
			} else {
				server = new SaslSocketServer(new SpecificResponder(ServerProto.class,
						this), new InetSocketAddress(IPAddress, 6788));
				pingTimer = new Timer();
				ps = new PingServer(this);
				pingTimer.scheduleAtFixedRate(ps, check_alive_interval, check_alive_interval);
			}
		}catch (IOException e){
			System.err.println("[Error] Failed to start server");
			System.exit(1);
		}
		server.start();
	}
	
	public void stopServer() {
		server.close();
		
		timer.cancel();
		timer.purge();
		pingTimer.cancel();
		pingTimer.purge();
	}
	
	public void pingServer() {
		try {
			
			Transceiver pingedServer = new SaslSocketTransceiver(new InetSocketAddress(originalIPAddress.toString(), 6789));
			ServerProto serverproxy = SpecificRequestor.getClient(ServerProto.class, pingedServer);
			
			// Original server back online. 
			this.sendControllerToController();
			// Let new server send its coordinates
			serverproxy.sendCoord();
			// Stop current server
			this.stopServer();
			pingedServer.close();
			
		} catch (Exception e) {
			// Continue pinging
		}
	}
	
	@Override
	public CharSequence sendCoord() throws AvroRemoteException {
		for (int key : uidmap.keySet()) {
			try {
				Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(uidmap.get(key).IPAddress.toString(), 6790 + key));
				if (uidmap.get(key).type == 0 && uidmap.get(key).is_online) {
					UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, trans);
					uproxy.ReceiveCoord(this.IPAddress, 6789);
				} else if (uidmap.get(key).type == 1 && uidmap.get(key).is_online) {
					SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, trans);
					sproxy.ReceiveCoord(this.IPAddress, 6789);
				} else if (uidmap.get(key).type == 2 && uidmap.get(key).is_online) {
					FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, trans);
					fproxy.ReceiveCoord(this.IPAddress, 6789);
				} else if (uidmap.get(key).type == 3 && uidmap.get(key).is_online) {
					LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, trans);
					lproxy.ReceiveCoord(this.IPAddress, 6789);
				}
				trans.close();
			} catch (IOException e) {
				System.err.println("[Error] Failed to send coordinates");
			}
		}
		return "";
	}
	
	
	/*****************
	 ** REPLICATION ** 
	 *****************/
	@Override
	public int sendController() throws AvroRemoteException {
		try {
			// Create JSON object
			JSONObject json = new JSONObject();
			json.put("nextID", nextID);
			json.put("sensormap", sensormap);
			json.put("uidalive", uidalive);
			json.put("fridgeAlive", fridgeAlive);
			json.put("originalIPAddress", originalIPAddress);
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
				String ip = uidmap.get(id).IPAddress.toString();
				if (type == 0 && uidmap.get(id).is_online) {
					// Send me to user
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					CharSequence response = userproxy.update_controller(json.toString());
					user.close();
				} else if (type == 2 && uidmap.get(id).is_online) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					CharSequence response = fridgeproxy.update_controller(json.toString());
					fridge.close();
				} else if (type == 1 && uidmap.get(id).is_online) {
					// Send uidmap to sensor
					Transceiver sensor = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					SensorProto sensorproxy = SpecificRequestor.getClient(SensorProto.class, sensor);
					CharSequence response = sensorproxy.update_uidmap(jsonuidmap.toString());
					sensor.close();
				} else if (type == 3 && uidmap.get(id).is_online) {
					// Send uidmap to light
					Transceiver light = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					LightProto lightproxy = SpecificRequestor.getClient(LightProto.class, light);
					CharSequence response = lightproxy.update_uidmap(jsonuidmap.toString());
					light.close();
				}
			}
		} catch (Exception e) {
			System.err.println("[Error] Failed to send controller");
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
			json.put("fridgeAlive", fridgeAlive);
			json.put("originalIPAddress", originalIPAddress);
			
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
				
				String ip = uidmap.get(id).IPAddress.toString();		
				int type = uidmap.get(id).type;
				
				if (type == 0 && uidmap.get(id).is_online) {
					// Send me to user
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					CharSequence response = userproxy.update_controller(json.toString());
					user.close();
					return;
				} else if (type == 2 && uidmap.get(id).is_online) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					CharSequence response = fridgeproxy.update_controller(json.toString());
					fridge.close();
					return;
				} else if (type == 1 && uidmap.get(id).is_online) {
					// Send uidmap to sensor
					Transceiver sensor = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					SensorProto sensorproxy = SpecificRequestor.getClient(SensorProto.class, sensor);
					CharSequence response = sensorproxy.update_uidmap(jsonuidmap.toString());
					sensor.close();
				} else if (type == 3 && uidmap.get(id).is_online) {
					// Send uidmap to light
					Transceiver light = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
					LightProto lightproxy = SpecificRequestor.getClient(LightProto.class, light);
					CharSequence response = lightproxy.update_uidmap(jsonuidmap.toString());
					light.close();
				}
			}
		} catch (Exception e) {
			System.err.println("Not to own id");
			System.err.println("[Error] Failed to send controller");
			e.printStackTrace();
			return;
		}
		return;
	}
	
	public void sendControllerToController() {
		try {
			// Create JSON object
			JSONObject json = new JSONObject();
			json.put("nextID", nextID);
			json.put("sensormap", sensormap);
			json.put("uidalive", uidalive);
			json.put("fridgeAlive", fridgeAlive);
			json.put("originalIPAddress", originalIPAddress);
			
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
			Transceiver pingedServer = new SaslSocketTransceiver(new InetSocketAddress(originalIPAddress.toString(), 6789));
			ServerProto serverproxy = SpecificRequestor.getClient(ServerProto.class, pingedServer);
			CharSequence response = serverproxy.updateController(json.toString());
			pingedServer.close();
		} catch (Exception e) {
			System.err.println("[Error] Failed to send controller");
			return;
		}
		return;
	}

	@Override
	public CharSequence updateController(CharSequence jsonController) throws AvroRemoteException {
		try {
			JSONObject json = new JSONObject(jsonController.toString());
			
			nextID = json.getInt("nextID");
			originalIPAddress = json.getString("originalIPAddress");
			
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
			
			// sensormap
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
			
			// uidalive
			uidalive.clear();
			JSONObject jsonuidalive = json.getJSONObject("uidalive");
			keys = jsonuidalive.keys();
			while (keys.hasNext()) {
				String nextKey = keys.next();
				int id = Integer.parseInt(nextKey);
				boolean alive = jsonuidalive.getBoolean(nextKey);
				uidalive.put(id, alive);
			}
			
			// fridgeAlive
			fridgeAlive.clear();
			JSONObject jsonfridgealive = json.getJSONObject("fridgeAlive");
			keys = jsonfridgealive.keys();
			while (keys.hasNext()) {
				String nextKey = keys.next();
				int id = Integer.parseInt(nextKey);
				boolean alive = jsonfridgealive.getBoolean(nextKey);
				fridgeAlive.put(id, alive);
			}
		} catch (Exception e) {
			System.err.println("[Error] Failed to update controller");
			return "updateController" + e.toString();
		}	
		return "";
	}
	
	
	/**************************
	 ** DEVICE FUNCTIONALITY **
	 **************************/
	
	@Override
	public CharSequence get_all_devices() throws AvroRemoteException {
		String out = "";
		out += "\nCurrently in session("+ this.uidmap.size()+ "):\n";
		out += "id / is online / device type \n";
		for(int id : uidmap.keySet())
		{
			String type = "";
			if(uidmap.get(id).type == 0)
				type = "User";
			else if(uidmap.get(id).type == 1)
				type = "Sensor";
			else if(uidmap.get(id).type == 2)
				type = "Fridge";
			else if(uidmap.get(id).type == 3)
				type = "Light";
			out += id + " " +  Boolean.toString(uidmap.get(id).is_online) + " " + type + "\n";
		}
		out += "\n";
		return out;
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
			if (Double.isNaN(mean/n)) {
				return "";
			} else {
				return Double.toString(mean/n);
			}

		}catch (Exception e){
			
		}
		return "";
	}

	public void printInSession(){
		System.out.println("Currently in session("+ this.uidmap.size()+ "):");
		System.out.println("id / is online / device type ");
		for(int id : uidmap.keySet())
		{
			String type = "";
			if(uidmap.get(id).type == 0)
				type = "User";
			else if(uidmap.get(id).type == 1)
				type = "Sensor";
			else if(uidmap.get(id).type == 2)
				type = "Fridge";
			else if(uidmap.get(id).type == 3)
				type = "Light";
			System.out.println(id + " " +  Boolean.toString(uidmap.get(id).is_online) + " " + type);
		}
		System.out.print("\n");
	}
	
	/*************************
	 ** LIGHT FUNCTIONALITY **
	 *************************/
	
	@Override
	public CharSequence getActiveLights() throws AvroRemoteException {
		JSONObject json = new JSONObject();
		JSONArray arr = new JSONArray();
		for (int id : this.uidmap.keySet()) {
			if (this.uidmap.get(id).type == 3 && this.uidmap.get(id).is_online) {
				arr.put(id);
			}
		}
		try {
			json.put("lights", arr);
		} catch (JSONException e) {
		}
		return json.toString();
	}
	
	@Override
	public CharSequence get_lights_state() throws AvroRemoteException {
		try {
			CharSequence lights = "";
			for (Map.Entry<Integer, Device> entry : uidmap.entrySet()) {
				Integer key = entry.getKey();
				Device device = entry.getValue();
				String ip = uidmap.get(key).IPAddress.toString();
				if (device.type == 3 && device.is_online) {
					Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+key));
					LightProto proxy = SpecificRequestor.getClient(LightProto.class, trans);
					CharSequence state = proxy.send_state();
					lights = lights.toString() + key + ": " + state + "\n";
					trans.close();
				}
			}
			return lights;
		} catch (IOException e) {
			System.err.println("[Error] Failed to get lights state");
		}
		return "";
	}
	
	private void turn_of_lights(){
		for(int key : uidmap.keySet()){
			if(uidmap.get(key).type == 3 && uidmap.get(key).is_online){
				Transceiver trans;
				String ip = uidmap.get(key).IPAddress.toString();
				try {
					trans = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+key));
					LightProto lightproxy = SpecificRequestor.getClient(LightProto.class, trans);
					lightproxy.turn_off();
					trans.close();
				} catch (IOException e) {
					System.err.println("[Error] Failed to turn of lights");
				}
			}
		}
	}
	
	private void turn_back_lights(){
		for(int key : uidmap.keySet()){
			if(uidmap.get(key).type == 3 && uidmap.get(key).is_online){
				Transceiver trans;
				String ip = uidmap.get(key).IPAddress.toString();
				try {
					trans = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+key));
					LightProto lightproxy = SpecificRequestor.getClient(LightProto.class, trans);
					lightproxy.turn_back();
					trans.close();
				} catch (IOException e) {
					System.err.println("[Error] Failed to turn lights back on");
				}
			}
		}
	}
	
	@Override
	public CharSequence switch_state_light(int uid) throws AvroRemoteException {
		Device light = uidmap.get(uid);
		if (light == null || light.type != 3) {
			return "{\"Switched\" : false, \"Error\" : \"[Error] light_id not found in current session.\"}";
		}
		try {
			String ip = uidmap.get(uid).IPAddress.toString();
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+uid));
			LightProto proxy = SpecificRequestor.getClient(LightProto.class, trans);
			CharSequence state = proxy.switch_state();
			this.sendController();
			trans.close();
			return "{\"Switched\" : true, \"Error\" : NULL}";
		} catch (IOException e) {
			System.err.println("[Error] Failed to switch light state");
		}
		return " ";
	}
	
	
	/*************************
	 ** ALIVE FUNCTIONALITY **
	 *************************/
	
	@Override
	public int i_am_alive(int uid) throws AvroRemoteException {
		if (uidmap.get(uid).type == 0) {
			this.uidalive.put(uid, true);
		} else {
			this.fridgeAlive.put(uid, true);
		}
		return 0;
	}
	
	private int old_nr_users = 0;
	public void check_alive(){
		// Check alive users
		int nr_users = 0;
		for(int i : this.uidalive.keySet()){
			this.uidmap.get(i).is_online = this.uidalive.get(i);
			this.uidalive.put(i, false);
			if(this.uidmap.get(i).is_online)
				nr_users++;
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
		
		if(nr_users == 0 && old_nr_users != 0){
			this.turn_of_lights();
		}
		else if (nr_users != 0 && old_nr_users == 0){
			this.turn_back_lights();
		}
		old_nr_users = nr_users;
	}
	

	/*********************
	 ** FRIDGE FUNTIONS **
	 *********************/
	@Override
	public CharSequence get_fridge_contents(int uid) throws AvroRemoteException {
		Device fridge = uidmap.get(uid);
		if (fridge == null || fridge.type != 2) {
			return "{\"Contents\" : NULL, \"Error\" : \"[Error] fridge_id not found in current session.\"}";
		}
		try {
			String ip = fridge.IPAddress.toString();
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+uid));
			FridgeProto proxy = SpecificRequestor.getClient(FridgeProto.class, trans);
			CharSequence contents = proxy.send_current_items();
			trans.close();
			return "{\"Contents\" : " + contents + ", \"Error\" : NULL}";
		} catch (IOException e) {
			System.err.println("[Error] Failed to connect to fridge");
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
			String ip = fridge.IPAddress.toString();
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+uid));
			FridgeProto proxy = SpecificRequestor.getClient(FridgeProto.class, trans);
			CharSequence contents = proxy.send_all_items();
			System.out.println(contents);
			trans.close();
		} catch (IOException e) {
			System.err.println("[Error] Failed to connect to fridge");
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
			return "{\"socket\" : " + (fridgeid + 6790) + ", \"IPAddress\" : \"" + this.uidmap.get(fridgeid).IPAddress + "\"}";
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
					String ip = uidmap.get(uid).IPAddress.toString();
					if (type == 0) {
						// Send me to user
						Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(ip, 6790+id));
						UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
						int response = userproxy.notify_empty_fridge(uid);
						user.close();
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
			if((uidmap.get(key).type == 0 || uidmap.get(key).type == 2) && uidmap.get(key).is_online){
				out.put(key, uidmap.get(key).IPAddress);
			}
		}
		return out;
	}
	
	public Integer getNextID(int currentID) {
		List<Integer> keys = new ArrayList(this.uidmap.keySet());
		int nextID = 0;
		for (int i = 0; i < keys.size(); i++) {
			if (keys.get(i) == currentID && i != keys.size() - 1) {
				nextID = i + 1;
			} else if (keys.get(i) == currentID && i == keys.size() - 1) {
				nextID = 0;
			}
		}
		return nextID;
	}
	
	public CharSequence getIP(int id) {
		return this.uidmap.get(id).IPAddress;
	}
	
	public Map<Integer, Device> getUidmap(){
		return this.uidmap;
	}
	
	@Override
	public CharSequence getIDdevice(int devicetype) throws AvroRemoteException {
		String out = "";
		for(int id : this.uidmap.keySet()){
			if(this.uidmap.get(id).is_online && this.uidmap.get(id).type == devicetype)
				out += Integer.toString(id) + " ";
		}
		return out;
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
					user.close();
				}
			}
			return "{\"Error\" : NULL}";
		} catch (Exception e) {
			System.err.println("[Error] Failed to let user enter");
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
			for (int id : uidmap.keySet()) {
				if (id != uid && uidmap.get(id).type == 0 && uidmap.get(id).is_online) {
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					int response = userproxy.notify_user_leaves(uid);
					user.close();
				}
			}
			return "{\"Error\" : NULL}";
		} catch (Exception e) {
			System.err.println("[Error] Failed to let user leave");
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
		Controller controller = new Controller(server_ip, true);
		controller.runServer();
		
		System.out.println("Do you want to run silent(1) mode of interface mode(2)");
		int mode = reader.nextInt();
		if(mode == 1){
			System.out.println("Server started...");
			while(true){
				int a = 0;
			}
		}
		else if(mode == 2){
			while(true){
				System.out.println("What do you want to do?");
				System.out.println("1) Get in-session list");
				System.out.println("2) Get state light");
				System.out.println("3) Switch state light");
				System.out.println("4) Get contents fridge");
				System.out.println("5) Get current en removed contents fridge");
				System.out.println("6) Get temperature list");
				System.out.println("7) Get current temperature");
				
		//		System.out.println("8) send controller");
				
				int in = reader.nextInt();
				if(in == 1){
					
					controller.printInSession();
				} else if(in ==2){
					try {
						System.out.println(controller.get_lights_state());
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if(in ==3){
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
				} else if(in == 9){
					controller.turn_of_lights();
					
				} else {
					break;
				}
			}
			reader.close();
			//controller.get_light_state(0);
			controller.stopServer();
		}	
	}
}
