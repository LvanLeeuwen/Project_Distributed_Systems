package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.json.JSONObject;

import ihome.proto.sensorside.SensorProto;
import ihome.proto.serverside.ServerProto;
import ihome.proto.userside.UserProto;
import ihome.server.Controller;
import ihome.server.Device;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.lightside.LightProto;

public class Light implements LightProto {
	
	// Server variables
	private Server server = null;
	private Transceiver light;
	private ServerProto proxy;

	// Light variables
	private String name;
	private int ID;
	private String state = "off";
	private String IPAddress;
	private String server_ip_address;
	
	// Election variables
	private boolean participant = false;
	private int lastServerID = -1;
	
	// Controller variables
	private Map<Integer, Device> uidmap = new HashMap<Integer, Device>();
	
	// Alive caller variables
	private AliveCaller ac;
	private Timer timer;
	final static int wtna = Controller.check_alive_interval / 3; 
	
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public Light() {}
	public Light(String ip_address, String server_ip) {
		IPAddress = ip_address;
		server_ip_address = server_ip;
	}
	
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server() {
		try {
			light = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, light);
			
			CharSequence response = proxy.connect(3, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			
			if (!json.isNull("Error")) throw new Exception();
			
			ID = json.getInt("UID");
			name = "light" + ID;
			
			System.out.println("Connected to server with name " + name + " and ID " + ID);
			
			// Start timer for I'm alive
			timer = new Timer();
			ac = new AliveCaller(this);
			timer.scheduleAtFixedRate(ac, wtna, wtna);
			
		} catch (Exception e) {
			System.err.println("[Error] Failed to connect to server");
			System.exit(1);
		}
	}
	
	public void runServer() {
		try
		{
			server = new SaslSocketServer(new SpecificResponder(LightProto.class,
					this), new InetSocketAddress(IPAddress, 6790+ID));
		}catch (IOException e){
			System.err.println("[Error] Failed to start server");
			System.exit(1);
		}
		server.start();
	}
	
	public void stopServer() {
		try {
			server.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void pullServer() {
		try {
			proxy.sendController();
		} catch (AvroRemoteException e) {
			System.err.println("[Error] Failed to pull from server");
		}
	}
	
	
	/*************************
	 ** LIGHT FUNCTIONALITY **
	 *************************/
	
	@Override
	public CharSequence send_state() throws AvroRemoteException {
		return "{\"state\" : \"" + state + "\"}";
	}

	@Override
	public CharSequence switch_state() throws AvroRemoteException {
		if (state == "on") {
			state = "off";
			System.out.println("Light with name " + name + " and ID " + ID + " is switched off.");
		} else {
			state = "on";
			System.out.println("Light with name " + name + " and ID " + ID + " is switched on.");
		}
		return "{\"Error\" : NULL}";
	}
	
	
	private String oldstate = "off";
	
	@Override
	public int turn_off() throws AvroRemoteException {
		oldstate = state;
		state = "off";
		return 0;
	}
	
	@Override
	public int turn_back() throws AvroRemoteException {
		state = oldstate;
		return 0;
	}
	

	/************
	 * ELECTION *
	 ************/
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
		if(nextID == this.lastServerID){
			return false;
		}
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.uidmap.get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.Callback.class, cand);
				uproxy.receiveElection(receivedID);
			} else if (this.uidmap.get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.Callback.class, cand);
				sproxy.receiveElection(receivedID);
			} else if (this.uidmap.get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.Callback.class, cand);
				fproxy.receiveElection(receivedID);
			} else if (this.uidmap.get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.Callback.class, cand);
				lproxy.receiveElection(receivedID);
			}
			cand.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public boolean sendElected(int nextID, CharSequence ipaddress, CharSequence serverIP, int port, int sid) {
		if(nextID == this.lastServerID){
			return false;
		}
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.uidmap.get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.Callback.class, cand);
				uproxy.receiveElected(serverIP, port, sid);
			} else if (this.uidmap.get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.Callback.class, cand);
				sproxy.receiveElected(serverIP, port, sid);
			} else if (this.uidmap.get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.Callback.class, cand);
				fproxy.receiveElected(serverIP, port, sid);
			} else if (this.uidmap.get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.Callback.class, cand);
				lproxy.receiveElected(serverIP, port, sid);
			}
			cand.close();
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
	public CharSequence receiveElected(CharSequence serverIP, int port, int serverID) throws AvroRemoteException {
		if (this.participant) {
			this.participant = false;
			this.server_ip_address = serverIP.toString();
			try {
				light = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
				proxy = SpecificRequestor.getClient(ServerProto.Callback.class, light);
				this.lastServerID = serverID;
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			// Forward elected message
			int nextID = this.getNextID(this.ID);
			CharSequence nextIP = this.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, serverIP, port, serverID)) {
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
			light = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, light);
			this.lastServerID = -1;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("[Error] Failed to start server");
		}
		return 0;
	}
	
	
	/*****************
	 * UPDATE UIDMAP *
	 *****************/
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
			System.err.println("[Error] Failed to update uidmap");
			return "update_uidmap" + e.toString();
		}	
		return " ";
	}
	
	/*************************
	 ** ALIVE FUNCTIONALITY **
	 *************************/
	
	public void send_alive(){
		try {
			proxy.i_am_alive(this.ID);	
		} catch (AvroRemoteException e) {
		}
	}
	
	
	
	/**********
	 ** MAIN ** 
	 **********/
	
	public static void main(String[] args) {
		// Connect to server
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String ip_address = reader.nextLine();
		System.out.println("What is the servers IP address?");
		String server_ip = reader.nextLine();
		Light myLight = new Light(ip_address, server_ip);
		
		myLight.connect_to_server();
		myLight.runServer();
		myLight.pullServer();
		
		reader.close();
		
		while(true){
			continue;
		}
		//myLight.stopServer();
	}
	
	
	
}
