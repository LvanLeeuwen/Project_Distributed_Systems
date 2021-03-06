package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.InputMismatchException;
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
import org.json.*;

import ihome.server.Controller;
import ihome.server.Device;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.serverside.ServerProto;
import ihome.proto.userside.UserProto;
import ihome.proto.lightside.LightProto;
import ihome.proto.sensorside.SensorProto;

public class User implements UserProto {
	
	final static int wtna = Controller.check_alive_interval / 3; 
	
	// Server variables
	private Controller controller;
	private Server server = null;
	private Transceiver user;
	private ServerProto proxy;
	
	// User variables
	private String name;
	private int ID;
	private String IPAddress;
	private String server_ip_address;
	private Boolean inHouse = true;
	
	// Alive caller variables
	private AliveCaller ac;
	private Timer timer;
	
	//leader election variables
	private Boolean participant = false;
	private Boolean isLeader = false;
	private int elected = -1;
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public User() {}
	public User(String ip_address, String server_ip) {
		IPAddress = ip_address;
		server_ip_address = server_ip;
		controller = new Controller(ip_address, false);
	}
	
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server() {
		try {
			try {
				user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
			} catch (Exception e) {
				user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
				elected = -2;
			}
			
			CharSequence response = proxy.connect(0, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			
			ID = json.getInt("UID");
			name = "user" + ID;
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
			server = new SaslSocketServer(new SpecificResponder(UserProto.class,
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
	 ** ALIVE FUNCTIONALITY **
	 *************************/
	public void send_alive(){
		try {
			if (inHouse) {
				proxy.i_am_alive(this.ID);
			}
		} catch (AvroRemoteException e) {
			if (!this.participant) {
				this.startElection();
			}
		} 
	}
	
	
	/****************************
	 ** ELECTION FUNCTIONALITY **
	 ****************************/
	public boolean sendElection(int nextID, CharSequence ipaddress, int receivedID) {
		if(nextID == this.elected){
			return false;
		}
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.controller.getUidmap().get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.Callback.class, cand);
				uproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.Callback.class, cand);
				sproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.Callback.class, cand);
				fproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 3){
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
		if(nextID == this.elected){
			return false;
		}
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.controller.getUidmap().get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.Callback.class, cand);
				uproxy.receiveElected(serverIP, port, sid);
			} else if (this.controller.getUidmap().get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.Callback.class, cand);
				sproxy.receiveElected(serverIP, port, sid);
			} else if (this.controller.getUidmap().get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.Callback.class, cand);
				fproxy.receiveElected(serverIP, port, sid);
			} else if (this.controller.getUidmap().get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.Callback.class, cand);
				lproxy.receiveElected(serverIP, port, sid);
			}
			cand.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public CharSequence startElection() {
		this.participant = true;
		
		int nextID = this.controller.getNextID(this.ID);
		if (nextID != this.ID) {
			CharSequence nextIP = this.controller.getIP(nextID);
			
			while (!this.sendElection(nextID, nextIP, this.ID)) {
				nextID = this.controller.getNextID(nextID);
				if (nextID == this.ID) {
					// Start my server
					this.controller.runServer();
					try {
						user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
						proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
						this.elected = this.ID;
						this.server_ip_address = this.IPAddress;
						System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
					} catch (IOException e) {
						System.err.println("[Error] Failed to start server");
					}
					this.participant = false;
					return null;
				}
				nextIP = this.controller.getIP(nextID);
			}
		} else {
			// Start my server
			this.controller.runServer();
			try {
				user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);	
				this.elected = this.ID;
				this.server_ip_address = this.IPAddress;
				System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			this.participant = false;
		}
		return null;
	}
	
	@Override
	public CharSequence receiveElection(int receivedID) throws AvroRemoteException {
		if (receivedID > this.ID) {
			// Forward the election message
			this.participant = true;
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElection(nextID, nextIP, receivedID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		} else if (receivedID < this.ID && !this.participant) {
			// Send my ID
			this.participant = true;
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElection(nextID, nextIP, this.ID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		} else if (receivedID < this.ID && this.participant) {
			// Discard election message
		} else if (receivedID == this.ID) {
			// I'm the leader
			this.participant = false;
			// Start my server
			this.controller.runServer();
			try {
				user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);			
				this.elected = this.ID;
				this.server_ip_address = this.IPAddress;
				System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, this.IPAddress, 6788, this.ID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
			
		}
		return " ";
	}
	
	@Override
	public CharSequence receiveElected(CharSequence serverIP, int port, int serverID) throws AvroRemoteException {
		if (this.participant) {
			this.participant = false;
			this.server_ip_address = serverIP.toString();
			try {
				user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
				proxy = SpecificRequestor.getClient(ServerProto.Callback.class, user);
				this.elected = serverID;
				System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			// Forward elected message
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, serverIP, port, serverID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		} else {
			// Discard. Election is over.
			System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
		}
		return " ";
	}
	
	@Override
	public int ReceiveCoord(CharSequence server_ip, int port) throws AvroRemoteException {
		this.server_ip_address = server_ip.toString();
		try {
			user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, user);
			this.elected = -1;
			System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
		} catch (IOException e) {
			System.err.println("[Error] Failed to start server");
		}
		this.participant = false;
		this.isLeader = false;
		return 0;
	}
	
	@Override
	public CharSequence getLeader() throws AvroRemoteException {
		try {
			JSONObject json = new JSONObject();
			json.put("lastServerID", elected);
			return json.toString();
		} catch (JSONException e) {
			return "";
		}
	}
	
	public void askLeaderID() {
		CharSequence response = "";
		Map<Integer, Device> uidmap = this.controller.getUidmap();
		for (int id : uidmap.keySet()) {
			try {
				if (uidmap.get(id).type == 0 && uidmap.get(id).is_online) {
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					response = userproxy.getLeader();
					user.close();
				} else if (uidmap.get(id).type == 2 && uidmap.get(id).is_online) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					response = fridgeproxy.getLeader();
					fridge.close();
				} else if (uidmap.get(id).type == 1 && uidmap.get(id).is_online) {
					// Send uidmap to sensor
					Transceiver sensor = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					SensorProto sensorproxy = SpecificRequestor.getClient(SensorProto.class, sensor);
					response = sensorproxy.getLeader();
					sensor.close();
				} else if (uidmap.get(id).type == 3 && uidmap.get(id).is_online) {
					// Send uidmap to light
					Transceiver light = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					LightProto lightproxy = SpecificRequestor.getClient(LightProto.class, light);
					response = lightproxy.getLeader();
					light.close();
				}
			} catch (Exception e) {
				continue;
			}
			if (response != "") break;
		}
		// Unpack response
		if (response != "") {
			JSONObject json;
			try {
				json = new JSONObject(response.toString());
				elected = json.getInt("lastServerID");
			} catch (JSONException e) {
				System.err.println("[Error] JSON exception");
			}
		}
	}
	
	
	/********************
	 ** FRIDGE CONNECT **
	 ********************/
	
	private CharSequence getPort(int uid){
		try {
			CharSequence response = proxy.get_fridge_port(this.ID, uid);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("socket"))
				return response;
			else{
				try{
					System.out.println("[Error] " + json.getString("Error"));
				}
				catch(Exception e){
					
				}
				return "";
			}
		} catch (AvroRemoteException | JSONException e) {
			System.err.println("[Error] Failed to get port");
		}
		return "";
	}
	
	public void connectToFridge(int fridgeid) 
	{
		CharSequence fridgeData = getPort(fridgeid);
		if (fridgeData == "") {
			System.out.println("No fridge connected with id " + fridgeid + "\n");
			return;
		}
		int port = -1;
		CharSequence ip = "0";
		try {
			JSONObject json = new JSONObject(fridgeData.toString());
			port = json.getInt("socket");
			ip = json.getString("IPAddress");
		} catch (JSONException e) {
			System.err.println("[Error] JSON error");
		}
		
		if(port == -1)
		{
			System.out.println("[Error] Couldn't connect to fridge.");
			return;
		}
		
		try {
			Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(ip.toString(), port));
			FridgeProto fridgeproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, fridge);
			
			Scanner reader = new Scanner(System.in);
			while(true){
				System.out.println("What do you want to do?");
				System.out.println("1) Add item to fridge(" + fridgeid + ")");
				System.out.println("2) remove item from fridge(" + fridgeid+ ")");
				System.out.println("3) Show current items in fridge");
				System.out.println("4) Exit");
				try {
					int in = reader.nextInt();
					if(in == 1){		// Get list of all devices and users.
						reader.nextLine(); // Consume newline left-over
						System.out.println("What do you want to add to the fridge?");
						String item = reader.nextLine();
						fridgeproxy.add_item(item);
					} else if(in == 2){
						reader.nextLine(); // Consume newline left-over
						System.out.println("Current items in fridge:");
						System.out.println(fridgeproxy.send_current_items());
						System.out.println("What do you want to remove from the fridge?");
						String item = reader.nextLine();
						fridgeproxy.remove_item(item);
					}else if(in == 3){	// Get overview of the state of all the lights.					
						System.out.println(fridgeproxy.send_current_items());
					} else if(in == 4){
						break;
					} else {
						continue;
					}
				} catch (InputMismatchException e) {
					System.out.println("Wrong input.");
					reader.next();
				}
			}
			proxy.release_fridge(fridgeid);
				
		} catch (IOException e) {
			try {
				proxy.report_offline(fridgeid);
			} catch (AvroRemoteException e1) {
				System.err.println("[Error] Failed to report fridge offline");
			}
			System.err.println("Fridge is offline");
		} 
	}
	
	@Override
	public int notify_empty_fridge(int fid) throws AvroRemoteException {
		System.out.println("Fridge with ID " + fid + " is empty!");
		return 0;
	}
	
	
	/************************
	 * CONTROLLER FUNCTIONS *
	 ************************/
	@Override
	public CharSequence update_controller(CharSequence jsonController) throws AvroRemoteException {
		if (this.elected != this.ID) {
			controller.updateController(jsonController);
		}
		return "";
	}
	

	/*********************
	 * ENTER/LEAVE HOUSE *
	 *********************/
	public void enterHouse() {
		if (!inHouse) {
			inHouse = true;
		}
		// Notify controller
		try {
			proxy.user_enters(this.ID);
			System.out.println("I entered the house!");
		} catch (AvroRemoteException e) {
			System.err.println("[Error] Failed to let user enter");
		}
		return;
	}
	
	public void leaveHouse() {
		if (inHouse) {
			inHouse = false;
		}
		// Notify controller
		try {
			proxy.user_leaves(this.ID);
			System.out.println("I left the house!");
		} catch (AvroRemoteException e) {
			System.err.println("[Error] Failed to let user leave");
		}
		return;
	}
	
	@Override
	public int notify_user_enters(int uid) throws AvroRemoteException {
		System.out.println("User " + uid + " entered the house!");
		return 0;
	}
	@Override
	public int notify_user_leaves(int uid) throws AvroRemoteException {
		System.out.println("User " + uid + " left the house!");
		return 0;
	}
	
	
	public void get_content_fridge(int id){
		try {
			
			CharSequence result = this.proxy.get_fridge_contents(id);
			JSONObject json = new JSONObject(result.toString());
			
			try{
				if(json.get("Contents") == JSONObject.NULL)
					throw new Exception();
				
				System.out.println(json.get("Contents").toString());
				
			} catch(Exception e){
				if(json.get("Error") != null)
					{System.out.println("An error occurred: " + json.get("Error"));}
				else{
					throw e;
				}
				
			}
		} catch (Exception e) {
			System.err.println("[Error] Failed to get fridge contents");
		}
	}
	
	
	/**********
	 ** MAIN **
	 **********/
	public static void main(String[] args) {
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String ip_address = reader.nextLine();
		System.out.println("What is the servers IP address?");
		String server_ip = reader.nextLine();
		User myUser = new User(ip_address, server_ip);
		
		myUser.connect_to_server();
		myUser.runServer();
		myUser.pullServer();
		if (myUser.elected == -2) {
			// Ask leader ID
			myUser.askLeaderID();
		}
		
		
		while (true) {

			/*
			 * Possible actions:
			 * 		Exit the system (disconnect from server)
			 * 		Ask controller for list of all devices and other users
			 * 		Ask controller for overview of the state of all the lights
			 * 		Ask controller to switch specific light to another state
			 * 		Ask controller for overview of inventory of a fridge
			 * 		Ask controller to open a fridge 
			 * 		Ask opened fridge to add/remove items.
			 * 		Ask opened fridge to close a fridge.
			 * 		Ask controller for current temperature in the house.
			 * 		Ask controller for history of temperature in the house.
			 */
			System.out.println("What do you want to do?");
			if (myUser.inHouse) {
				System.out.println("1) Get list of all devices and users");
				System.out.println("2) Get overview of the state of all the lights");
				System.out.println("3) Switch light on/off");
				System.out.println("4) Get contents fridge");
				System.out.println("5) Get current temperature");
				System.out.println("6) Get history of temperature");
				System.out.println("7) Open fridge");
				System.out.println("8) Leave house");
			} else {
				System.out.println("9) Enter house");
			}

			try {
				int in = reader.nextInt();
				if(in == 1 && myUser.inHouse){		// Get list of all devices and users.
					try {
						CharSequence devices = myUser.proxy.get_all_devices();
						System.out.println(devices);
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if(in ==2 && myUser.inHouse){	// Get overview of the state of all the lights.
					try {
						CharSequence result = myUser.proxy.get_lights_state();
						if (result.toString() != "") {
							System.out.println(result);
						} else {
							System.out.println("\nNo lights connected\n");
						}
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if(in ==3 && myUser.inHouse){	// Switch state light
					try {
						CharSequence response = myUser.proxy.getIDdevice(3);
						if (response.toString() != "") {
							System.out.println("\nOnline lights: " + response);
							System.out.println("Give id:");
							int id = reader.nextInt();
							CharSequence result = myUser.proxy.switch_state_light(id);
							JSONObject jsonResult = new JSONObject(result.toString());
							boolean switched = jsonResult.getBoolean("Switched");
							if (!switched) {
								String error = jsonResult.getString("Error");
								System.out.println("Failed to switch light. An error occured: " + error + "\n");
							}
						} else {
							System.out.println("There are no online lights.\n");
						}
					} catch (Exception e) {
					}
				} else if (in == 4 && myUser.inHouse) {	// Get contents fridge.			
					try {
						CharSequence response = myUser.proxy.getIDdevice(2);
						if (response.toString() != "") {
							System.out.println("\nOnline fridges: " + response);	
							System.out.println("Give id:");
							int id = reader.nextInt();
							myUser.get_content_fridge( id);
						} else {
							System.out.println("\nThere are no online fridges.\n");
						}
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (in == 5 && myUser.inHouse) {	// Get current temperature.
					try {
						CharSequence result = myUser.proxy.get_temperature_current();
						if (result.toString() != "") {
							System.out.println(result);
						} else {
							System.out.println("\nNo temperature sensor connected. \n");
						}
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (in == 6 && myUser.inHouse) { // Get history of temperature
					try {
						CharSequence result = myUser.proxy.get_temperature_list();
						System.out.println(result);
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	
				} else if(in == 7 && myUser.inHouse){
					try {
						CharSequence response = myUser.proxy.getIDdevice(2);
						if (response.toString() != "") {
							System.out.println("\nOnline fridges: " + response);	
							System.out.println("Give id:");
							int id = reader.nextInt();
							myUser.connectToFridge(id);
						} else {
							System.out.println("There are no online fridges.\n");
						}
					} catch (AvroRemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (in == 8 && myUser.inHouse) {
					myUser.leaveHouse();
				} else if (in == 9 && !myUser.inHouse) {
					myUser.enterHouse();
				}else {
					System.out.println("Wrong input.");
					continue;
				}
			} catch (InputMismatchException e) {
				System.out.println("Wrong input.");
				reader.next();
			}
			
		}
		//reader.close();
	}
}
	
	


