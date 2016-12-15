package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
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
			user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
			
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
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.controller.getUidmap().get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
				uproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
				sproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
				fproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
				lproxy.receiveElection(receivedID);
			}
			cand.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public boolean sendElected(int nextID, CharSequence ipaddress, CharSequence serverIP, int port) {
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.controller.getUidmap().get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
				uproxy.receiveElected(serverIP, port);
			} else if (this.controller.getUidmap().get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
				sproxy.receiveElected(serverIP, port);
			} else if (this.controller.getUidmap().get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
				fproxy.receiveElected(serverIP, port);
			} else if (this.controller.getUidmap().get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
				lproxy.receiveElected(serverIP, port);
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
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, this.IPAddress, 6788)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		}
		return " ";
	}
	
	@Override
	public CharSequence receiveElected(CharSequence serverIP, int port) throws AvroRemoteException {
		if (this.participant) {
			this.participant = false;
			this.server_ip_address = serverIP.toString();
			try {
				user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
				proxy = SpecificRequestor.getClient(ServerProto.Callback.class, user);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			// Forward elected message
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, this.IPAddress, 6788)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
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
			user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, user);
		} catch (IOException e) {
			System.err.println("[Error] Failed to start server");
		}
		this.participant = false;
		this.isLeader = false;
		return 0;
	}
	
	
	/********************
	 ** FRIDGE CONNECT **
	 ********************/
	
	private int getPort(int uid){
		try {
			CharSequence response = proxy.get_fridge_port(this.ID, uid);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("socket"))
				return json.getInt("socket");
			else{
				try{
					System.out.println("[Error] " + json.getString("Error"));
				}
				catch(Exception e){
					
				}
				return -1;
			}
		} catch (AvroRemoteException | JSONException e) {
			System.err.println("[Error] Failed to get port");
		}
		return -1;
	}
	
	public void connectToFridge(int fridgeid) 
	{
		int port = getPort(fridgeid);
		
		if(port == -1)
		{
			System.out.println("[Error] Couldn't connect to fridge.");
			return;
		}
		
		try {
			Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(port));
			FridgeProto fridgeproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, fridge);
			
			Scanner reader = new Scanner(System.in);
			while(true){
				System.out.println("What do you want to do?");
				System.out.println("1) Add item to fridge(" + fridgeid + ")");
				System.out.println("2) remove item from fridge(" + fridgeid+ ")");
				System.out.println("3) Show current items in fridge");
				System.out.println("4) Exit");
				int in = reader.nextInt();
				if(in == 1){		// Get list of all devices and users.
					reader.nextLine(); // Consume newline left-over
					System.out.println("What do you want to add to the fridge?");
					String item = reader.nextLine();
					fridgeproxy.add_item(item);
				} else if(in == 2){
					reader.nextLine(); // Consume newline left-over
					System.out.println("What do you want to remove from the fridge?");
					String item = reader.nextLine();
					fridgeproxy.remove_item(item);
				}else if(in == 3){	// Get overview of the state of all the lights.					
					System.out.println(fridgeproxy.send_current_items());
				} else if(in == 4){
					break;
				}	
			}
			reader.close();			
			proxy.release_fridge(fridgeid);
			fridge.close();
				
		} catch (IOException e) {
			try {
				proxy.report_offline(fridgeid);
			} catch (AvroRemoteException e1) {
				System.err.println("[Error] Failed to report fridge offline");
			}
			System.err.println("[Error] Failed to connect to fridge");
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
		controller.updateController(jsonController);
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
		
		while (true) {

			/*
			 * Possible actions:
			 * 		Exit the system (disconnect from server)
			 * 		Ask controller for list of all devices and other users
			 * 		Ask controller for overview of the state of all the lights
			 * 		Ask controller to switch specif@param argsic light to another state
			 * 		Ask controller for overview of inventory of a fridge
			 * 		Ask controller to open a fridge 
			 * 		Ask opened fridge to add/remove items.
			 * 		Ask opened fridge to close a fridge.
			 * 		Ask controller for current temperature in the house.
			 * 		Ask controller for history of temperature in the house.
			 */
			System.out.println("What do you want to do?");
			System.out.println("1) Get list of all devices and users");
			System.out.println("2) Get overview of the state of all the lights");
			System.out.println("3) Switch light on/off");
			System.out.println("4) Get contents fridge");
			System.out.println("5) Get current temperature");
			System.out.println("6) Get history of temperature");
			System.out.println("7) Open fridge");
			System.out.println("8) Leave house");
			System.out.println("9) Enter house");

			
			int in = reader.nextInt();
			if(in == 1){		// Get list of all devices and users.
				try {
					
					
					CharSequence devices = myUser.proxy.get_all_devices();
					System.out.println(devices);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if(in ==2){	// Get overview of the state of all the lights.
				try {
					CharSequence result = myUser.proxy.get_lights_state();
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if(in ==3){	// Switch state light
				System.out.println("Give id of light:");
				int id = reader.nextInt();
				try {
					CharSequence result = myUser.proxy.switch_state_light(id);
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 4) {	// Get contents fridge.
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					CharSequence result = myUser.proxy.get_fridge_contents(id);
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 5) {	// Get current temperature.
				try {
					CharSequence result = myUser.proxy.get_temperature_current();
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 6) { // Get history of temperature
				try {
					CharSequence result = myUser.proxy.get_temperature_list();
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			} else if(in == 7){
				System.out.println("Give id:");
				int id = reader.nextInt();
				myUser.connectToFridge(id);
			} else if (in == 8) {
				myUser.leaveHouse();
			} else if (in == 9) {
				myUser.enterHouse();
			}else {
			
				break;
			}
			
			
		}
		reader.close();
	}
}
	
	


