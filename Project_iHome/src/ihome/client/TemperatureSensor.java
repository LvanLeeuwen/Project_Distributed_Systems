package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.ipc.CallFuture;
import org.json.JSONObject;

import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.lightside.LightProto;
import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;
import ihome.server.Device;
import ihome.proto.sensorside.SensorProto;
import ihome.proto.userside.UserProto;

public class TemperatureSensor implements SensorProto {

	// Variables to set up a connection with the server.
	private Transceiver sensor;
	private ServerProto.Callback proxyASynchrone;
	private CallFuture<CharSequence> future = new CallFuture<CharSequence>();
	private Server server = null;
	private ServerProto proxy;
	
	// Variables specifically for the temperature sensor
	private int ID;
	private String name;
	private float temperature;
	private ArrayList<Float> unsendTemperatures = new ArrayList<Float>();
	private String IPAddress;
	private String server_ip_address;
	
	// Election variables
	private boolean participant = false;
	
	// Controller variables
	private Map<Integer, Device> uidmap = new HashMap<Integer, Device>();
	
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public TemperatureSensor() {}
	public TemperatureSensor(String ip_address, String server_ip) {
		IPAddress = ip_address;
		server_ip_address = server_ip;
	}
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server(float initTemp) {
		try {
			sensor = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxyASynchrone = SpecificRequestor.getClient(ServerProto.Callback.class, sensor);
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, sensor);
			System.out.println("Connected to server");
			CharSequence response = proxyASynchrone.connect(1, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "sensor" + ID;
			temperature = initTemp;
			System.out.println("name: " + name + " ID: " + ID);
		} catch (Exception e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
	
	public void runServer() {
		try
		{
			server = new SaslSocketServer(new SpecificResponder(LightProto.class,
					this), new InetSocketAddress(IPAddress, 6790+ID));
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
	
	public void pullServer() {
		try {
			proxyASynchrone.sendController();
		} catch (AvroRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*******************************
	 ** TEMPERATURE FUNCTIONALITY **
	 *******************************/
	public void sent_temperature() {
		// Calculate new temperature
		float rangeMin = -1.0f;
		float rangeMax = 1.0f;
		Random r = new Random();
		float value = rangeMin + (rangeMax - rangeMin) * r.nextFloat();
		temperature += value;
		
		// Try to send the new temperature to the server
		try {
			if (unsendTemperatures.isEmpty()) {
				proxyASynchrone.update_temperature(ID, temperature, future);
				JSONObject json = new JSONObject(future.get().toString());
				if (!json.isNull("Error")) {
					CharSequence error = json.getString("Error");
					System.out.println("Error: " + error);
				} 
			} else {
				// Send all unsent temperatures.
				unsendTemperatures.add(temperature);
				for (float temp : unsendTemperatures) {
					proxyASynchrone.update_temperature(ID, temp, future);
					JSONObject json = new JSONObject(future.get().toString());
					if (!json.isNull("Error")) {
						CharSequence error = json.getString("Error");
						System.out.println("Error: " + error);
					} else {
						System.out.println("Verzonden unsend");
						unsendTemperatures.remove(temp);
					}
				}
			}
		} catch (ExecutionException e) {
			if (unsendTemperatures.isEmpty()) {
				unsendTemperatures.add(temperature);
				System.out.println("Toegevoegd aan unsend");
			} else {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/****************************
	 ** ELECTION FUNCTIONALITY **
	 ****************************/
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
	
	public boolean sendElection(int nextID, CharSequence ipaddress, int receivedID) {
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.uidmap.get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
				uproxy.receiveElection(receivedID);
			} else if (this.uidmap.get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
				sproxy.receiveElection(receivedID);
			} else if (this.uidmap.get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
				fproxy.receiveElection(receivedID);
			} else if (this.uidmap.get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
				lproxy.receiveElection(receivedID);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public boolean sendElected(int nextID, CharSequence ipaddress, CharSequence serverIP, int port) {
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.uidmap.get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
				uproxy.receiveElected(serverIP, port);
			} else if (this.uidmap.get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
				sproxy.receiveElected(serverIP, port);
			} else if (this.uidmap.get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
				fproxy.receiveElected(serverIP, port);
			} else if (this.uidmap.get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
				lproxy.receiveElected(serverIP, port);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	@Override
	public CharSequence receiveElection(int receivedID) throws AvroRemoteException {
		this.participant = true;
		int nextID = this.getNextID(this.ID);
		CharSequence nextIP = this.getIP(nextID);
		while (!this.sendElection(nextID, nextIP, receivedID)) {
			nextID = this.getNextID(nextID);
			nextIP = this.getIP(nextID);
		}
		return " ";
	}
	
	@Override
	public CharSequence receiveElected(CharSequence serverIP, int port) throws AvroRemoteException {
		if (this.participant) {
			this.participant = false;
			this.server_ip_address = serverIP.toString();
			try {
				sensor = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
				proxyASynchrone = SpecificRequestor.getClient(ServerProto.Callback.class, sensor);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("leader elected!");
			// Forward elected message
			int nextID = this.getNextID(this.ID);
			CharSequence nextIP = this.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, serverIP, port)) {
				nextID = this.getNextID(nextID);
				nextIP = this.getIP(nextID);
			}
		} else {
			// Discard. Election is over.
		}
		return " ";
	}
	
	@Override
	public int ReceiveCoord(CharSequence server_ip, int port) throws AvroRemoteException {
		this.server_ip_address = server_ip.toString();
		try {
			sensor = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
			proxyASynchrone = SpecificRequestor.getClient(ServerProto.Callback.class, sensor);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("New leader: " + server_ip);
		return 0;
	}
	
	@Override
	public CharSequence update_uidmap(CharSequence json_uidmap) throws AvroRemoteException {
		try {
			JSONObject json = new JSONObject(json_uidmap.toString());
			
			/*
			 * uidmap:
			 * each device has:
			 * 		type 
			 * 		online value
			 * 		IPAddress
			 * 		has_local_connect
			 */
			this.uidmap.clear();
			Iterator<String> keys = json.keys();
			while (keys.hasNext()) {
				String nextKey = keys.next();
				int id = Integer.parseInt(nextKey);
				JSONObject devices = json.getJSONObject(nextKey);
				int type = devices.getInt("type");
				boolean online = devices.getInt("is_online") == 1 ? true : false;
				CharSequence ip_address = devices.getString("ip_address");
				int has_local_connect = devices.getInt("has_local_connect");
				this.uidmap.put(id, new Device(type, online, ip_address, has_local_connect));
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "update_uidmap" + e.toString();
		}	
		return " ";
	}
	
	/************************
	 ** MAIN FUNCTIONALITY **
	 ************************/
	public static void main(String[] args) {
		// Connect to server
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String ip_address = reader.nextLine();
		System.out.println("What is the servers IP address?");
		String server_ip = reader.nextLine();
		TemperatureSensor mySensor = new TemperatureSensor(ip_address, server_ip);
		
		System.out.println("What is the initial temperature of this sensor?");
		float initTemp = reader.nextFloat();
		mySensor.connect_to_server(initTemp);
		mySensor.runServer();
		mySensor.pullServer();
		mySensor.sent_temperature();
		
		// Set timer
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				mySensor.sent_temperature();
			}
		};
		// Sent temperature every 10 seconds
		timer.schedule(task, 10000, 10000);
		
		while (true) {
			// execute actions from command line
			/*
			 * Possible actions:
			 * 		
			 */
		}
	}

}
