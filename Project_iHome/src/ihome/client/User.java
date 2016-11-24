package ihome.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

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
	
	private Controller controller;
	private Server server = null;
	private Transceiver user;
	private ServerProto proxy;
	
	// User variables
	private String name;
	private int ID;
	private String IPAddress;
	private String server_ip_address;
	
	
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
		controller = new Controller(ip_address);
	}
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server() {
		try {
			user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(0, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "user" + ID;
			System.out.println("username: " + name + " ID: " + ID + " Entered the house");
			
			timer = new Timer();
			ac = new AliveCaller(this);
			
			timer.scheduleAtFixedRate(ac, wtna, wtna);
	
		} catch (Exception e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
	
	public void send_alive(){
		
		try {
			
			proxy.i_am_alive(this.ID);
			
		} catch (AvroRemoteException e) {
			
			if (!this.participant) {
				try {
					this.Election();
				} catch (AvroRemoteException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			/*
			System.err.println("[error] failed to send I'm alive");
			e.printStackTrace();
			*/
		}
	}
	
	public void exit() {
		try {
			CharSequence response = proxy.disconnect(ID);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			System.out.println("username: " + name + " ID: " + ID + " Leaved the house");
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	/******************************
	 ** CONTROLLER FUNCTIONALITY **
	 ******************************/
	
	/*public CharSequence startLeaderElection(){
		
		y
		
		// Check if there are no other candidates
		System.out.println(candidates.toString());
		System.out.println(L.toString());
		if(L.size() <= 0){
			System.out.println("L is empty" );
			this.participant = false;
			this.controller.runServer();
			
			// Make me the server
			try {
				if(!isLeader){
					user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6789));
					proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
					isLeader = true;
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
			// send Coordinator
			for (int key : S.keySet()) {
				try {
					Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(S.get(key).toString(), 6790 + key));
					if (this.controller.getUidmap().get(key).type == 0) {
						UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
						uproxy.ReceiveCoord(this.IPAddress);
					} else if (this.controller.getUidmap().get(key).type == 1) {
						SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
						sproxy.ReceiveCoord(this.IPAddress);
					} else if (this.controller.getUidmap().get(key).type == 2) {
						FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
						fproxy.ReceiveCoord(this.IPAddress);
					} else if (this.controller.getUidmap().get(key).type == 3) {
						LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
						lproxy.ReceiveCoord(this.IPAddress);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			return this.server_ip_address;
			
		} else{ //not the highest id
			this.participant = true;
			
			System.out.println("test");
			int number_of_failures = 0;
			
			//try to contact someone with a higher id
			for(int key : L.keySet()){
				try {
					Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(L.get(key).toString(), 6790 + key));
					if(this.controller.getUidmap().get(key).type == 0){
						UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
						return uproxy.Election();
					} else{
						FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
						return fproxy.Election();
					}
				} catch (IOException e) {
					number_of_failures++;
				}
			}
			/*
			//no one is reachable, choose yourself
			if (number_of_failures == L.size()) {
				try {
					user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6789));
					proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				// send Coordinator
				for (int key : S.keySet()) {
					try {
						Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(S.get(key).toString(), 6790 + key));
						if (this.controller.getUidmap().get(key).type == 0) {
							UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
							uproxy.ReceiveCoord(this.IPAddress);
						} else if (this.controller.getUidmap().get(key).type == 1) {
							SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
							sproxy.ReceiveCoord(this.IPAddress);
						} else if (this.controller.getUidmap().get(key).type == 2) {
							FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
							fproxy.ReceiveCoord(this.IPAddress);
						} else if (this.controller.getUidmap().get(key).type == 3) {
							LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
							lproxy.ReceiveCoord(this.IPAddress);
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				return this.server_ip_address;
			}
			
		}
		return null;
	}
	
	@Override
	public CharSequence Election() throws AvroRemoteException {
		if(!this.participant){
			return this.startLeaderElection();
		}
		return null;
	}*/
	
	
	
	@Override
	public CharSequence Election() throws AvroRemoteException{
		
		if(isLeader || participant){
			return " ";
		}
		this.participant = true;
		Map<Integer, CharSequence>candidates = this.controller.getPossibleParticipants();
		Map<Integer, CharSequence> L = new HashMap<Integer, CharSequence>();
		Map<Integer, CharSequence> S = new HashMap<Integer, CharSequence>();
		
		//init L and S
		for (int key : candidates.keySet()){
			if( key == this.ID)
				continue;
			else if(key > this.ID)
				L.put(key, candidates.get(key));
			else if(key < this.ID)
				S.put(key, candidates.get(key));
		}
		if(L.size() <= 0){
			
			
			this.controller.runServer();
			
			// Make me the server
			try {
				if(!isLeader){
					user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6789));
					proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
					isLeader = true;
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
			// send Coordinator
			for (int key : S.keySet()) {
				try {
					Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(S.get(key).toString(), 6790 + key));
					if (this.controller.getUidmap().get(key).type == 0) {
						UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
						uproxy.ReceiveCoord(this.IPAddress);
					} else if (this.controller.getUidmap().get(key).type == 1) {
						SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
						sproxy.ReceiveCoord(this.IPAddress);
					} else if (this.controller.getUidmap().get(key).type == 2) {
						FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
						fproxy.ReceiveCoord(this.IPAddress);
					} else if (this.controller.getUidmap().get(key).type == 3) {
						LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
						lproxy.ReceiveCoord(this.IPAddress);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			this.participant = false;
			
			return this.server_ip_address;
			
		} else{
			int number_of_failures = 0;
			
			for(int key : L.keySet()){
				try {
					Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(L.get(key).toString(), 6790 + key));
					if(this.controller.getUidmap().get(key).type == 0){
						UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
						return uproxy.Election();
					} else{
						FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
						return fproxy.Election();
					}
				} catch (IOException e) {
					number_of_failures++;
				}
			}
			
			if(number_of_failures == L.size()){
				
				this.controller.runServer();
				
				// Make me the server
				try {
					if(!isLeader){
						user = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6789));
						proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
						isLeader = true;
					}
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
				// send Coordinator
				for (int key : S.keySet()) {
					try {
						Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(S.get(key).toString(), 6790 + key));
						if (this.controller.getUidmap().get(key).type == 0) {
							UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
							uproxy.ReceiveCoord(this.IPAddress);
						} else if (this.controller.getUidmap().get(key).type == 1) {
							SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.class, cand);
							sproxy.ReceiveCoord(this.IPAddress);
						} else if (this.controller.getUidmap().get(key).type == 2) {
							FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
							fproxy.ReceiveCoord(this.IPAddress);
						} else if (this.controller.getUidmap().get(key).type == 3) {
							LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.class, cand);
							lproxy.ReceiveCoord(this.IPAddress);
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		
		
		this.participant = false;
		return " ";
		
	}
	
	
	
	
	
	
	
	
	
	@Override
	public int ReceiveCoord(CharSequence server_ip) throws AvroRemoteException {
		
		this.server_ip_address = server_ip.toString();
		try {
			user = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, user);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.participant = false;
		System.out.println("New leader: " + server_ip);
		return 0;
	}
	
	
	
	/******************************
	 ** CONTROLLER FUNCTIONALITY **
	 ******************************/
	
	@Override
	public CharSequence update_controller(CharSequence jsonController) throws AvroRemoteException {
		controller.updateController(jsonController);
		return "";
	}
	
	
	public void runServer() {
		try
		{
			server = new SaslSocketServer(new SpecificResponder(UserProto.class,
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
			proxy.sendController();
		} catch (AvroRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/********************
	 ** FRIDGE CONNECT **
	 ********************/
	
	private int getPort(int uid){
		try {
			CharSequence response = proxy.get_fridge_port(this.ID, uid);
			System.out.println(response);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("socket"))
				return json.getInt("socket");
			else{
				try{
					System.out.println(json.getString("Error"));
				}
				catch(Exception e){
					
				}
				return -1;
			}
		} catch (AvroRemoteException | JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			System.out.println(port);
			Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(port));
			FridgeProto fridgeproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, fridge);
			
			while(true){
				Scanner reader = new Scanner(System.in);
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
			
			
			proxy.release_fridge(fridgeid);
			
			
			
		} catch (IOException e) {
			try {
				proxy.report_offline(fridgeid);
			} catch (AvroRemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	@Override
	public int notify_empty_fridge(int fid) throws AvroRemoteException {
		System.out.println("Fridge " + fid + " is empty!");
		return 0;
	}
	
	/************************
	 * CONTROLLER FUNCTIONS *
	 ************************/
	
	public void useController()
	{
		Scanner reader = new Scanner(System.in);
		while(true){
			System.out.println("What do you want to do?");
			System.out.println("1) Get in-session list");
			//System.out.println("2) Get state light");
			System.out.println("3) Switch state light");
			System.out.println("4) Get contents fridge");
			System.out.println("5) Get current en removed contents fridge");
			System.out.println("6) Get temperature list");
			System.out.println("7) send controller");
			System.out.println("8) exit" );
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
				try {
					System.out.println(controller.get_temperature_list());
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 7) {
				try {
					controller.sendController();
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				return;
			}
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
			System.out.println("3) Switch state light");
			System.out.println("4) Get contents fridge");
			System.out.println("5) Get current temperature");
			System.out.println("6) Get history of temperature");
			System.out.println("7) Start connection with Fridge");
			System.out.println("8) Show my controllers devices");
			System.out.println("9) Start leader election");
			System.out.println("10) use Controller");

			
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
				System.out.println("Give id:");
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
				try {
					System.out.println(myUser.controller.get_all_devices());
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			
			} else if (in == 9) {
				try {
					myUser.Election();
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 10){
				myUser.useController();
			}else {
			
				break;
			}
			
			
		}
	}
}
	
	


