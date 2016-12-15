package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Timer;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.ipc.Server;
import org.json.JSONObject;

import ihome.proto.sensorside.SensorProto;
import ihome.proto.serverside.ServerProto;
import ihome.proto.userside.UserProto;
import ihome.server.Controller;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.lightside.LightProto;

public class Fridge implements FridgeProto {
	
	final static int wtna = Controller.check_alive_interval / 3; 
	
	private Server server = null;
	private Controller controller;
	private Transceiver fridge;
	private ServerProto proxy;

	// Fridge variables
	private String name;
	private int ID;
	private String IPAddress;
	private String server_ip_address;
	private boolean opened = false;
	private ArrayList<CharSequence> items = new ArrayList<CharSequence>();
	private ArrayList<CharSequence> allItems = new ArrayList<CharSequence>();
	
	// Alive caller variables
	private AliveCaller ac;
	private Timer timer;
	
	// Leader election variables
	private Boolean participant = false;
	private Boolean isLeader = false;
	
	
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public Fridge() {}
	public Fridge(String ip_address, String server_ip) {
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
				fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
			} catch (AvroRemoteException e) {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
			}
			CharSequence response = proxy.connect(2, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			
			ID = json.getInt("UID");
			name = "fridge" + ID;
			System.out.println("Connected to server with name " + name + " and ID: " + ID);
			
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
			server = new SaslSocketServer(new SpecificResponder(FridgeProto.class,
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
			proxy.i_am_alive(this.ID);
		} catch (AvroRemoteException e) {
			if (!this.participant) {
				this.startElection();
			}
		}
	}
	
	
	/**************
	 ** ELECTION **
	 **************/
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
						fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
						proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);				
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
				fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);				
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
				fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);				
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
				fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
				proxy = SpecificRequestor.getClient(ServerProto.Callback.class, fridge);
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
			fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, fridge);
		} catch (IOException e) {
			System.err.println("[Error] Failed to start server");
		}
		this.participant = false;
		this.isLeader = false;
		return 0;
	}
	
	
	/**************************
	 ** FRIDGE FUNCTIONALITY **
	 **************************/
	public void open() {
		opened = true;
	}
	
	public void close() {
		opened = false;
	}
	
	
	/**************************
	 ** ITEMS FUNCTIONALITY  **
	 **************************/
	public void print_items() {
		for (CharSequence item : items) {
			System.out.println(item);
		}
	}
	
	public ArrayList<CharSequence> get_items() {
		return items;
	}
	
	@Override
	public CharSequence add_item(CharSequence item) throws AvroRemoteException {
		
		
		if (items.contains(item)) {
			item = item +  "0";
		}
		items.add(item);
		//	this.add_item(item);
		return "test";
	}

	@Override
	public CharSequence remove_item(CharSequence item) throws AvroRemoteException {
		this.items.remove(item);
		if(this.items.isEmpty())
		proxy.notify_empty_fridge(this.ID);
		// TODO Auto-generated method stub
		return "test";
	}
	
	@Override
	public CharSequence send_current_items() throws AvroRemoteException {
	//	return "test";
		return Arrays.toString(items.toArray());
	}

	@Override
	public CharSequence send_all_items() throws AvroRemoteException {
		return Arrays.toString(allItems.toArray());
	}
	
	
	/******************************
	 ** CONTROLLER FUNCTIONALITY **
	 ******************************/
	
	@Override
	public CharSequence update_controller(CharSequence jsonController) throws AvroRemoteException {
		return controller.updateController(jsonController);
	}
	
	
	/**************************
	 ** MAIN FUNCTIONALITY   **
	 **************************/
	public static void main(String[] args) {
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String ip_address = reader.nextLine();
		System.out.println("What is the servers IP address?");
		String server_ip = reader.nextLine();
		Fridge myFridge = new Fridge(ip_address, server_ip);
		
		myFridge.connect_to_server();
		myFridge.runServer();
		myFridge.pullServer();
		
		while (true) {
			// execute actions from command line
			/*
			 * Possible actions:
			 * 		Print a list of items stored in the fridge.
			 * 		Return a list of all items stored in the fridge.
			 * 		Add or remove an item to or from the fridge.
			 */
			
			
			System.out.println("What do you want to do?");
			System.out.println("1) Get my controllers devices");
			System.out.println("2) Show contents");
			
			int in = reader.nextInt();
			if(in == 1){
				try {
					System.out.println(myFridge.controller.get_all_devices());
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 2) {
				myFridge.print_items();
				try {
					System.out.println(myFridge.send_current_items());
				} catch (AvroRemoteException e) {
					e.printStackTrace();
				}
			} else {
				break;
			}
		}
		reader.close();
	}
}
