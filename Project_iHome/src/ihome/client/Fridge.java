package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
	
	// Leader election variables
	private Boolean participant = false;
	
	
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public Fridge() {}
	public Fridge(String ip_address, String server_ip) {
		IPAddress = ip_address;
		server_ip_address = server_ip;
		controller = new Controller(ip_address);
	}
	
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server() {
		try {
			fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(2, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "fridge" + ID;
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
			server = new SaslSocketServer(new SpecificResponder(FridgeProto.class,
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
	
	/**************
	 ** ELECTION **
	 **************/
	public void startLeaderElection(){
		
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
		
		// Check if there are no other candidates
		if(L.size() <= 0){
			this.participant = false;
			this.controller.runServer();
			
			// Make me the server
			try {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6790 + ID));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
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
			
		} else{ //not the highest id
			this.participant = true;
			
			int number_of_failures = 0;
			
			//try to contact someone with a higher id
			for(int key : L.keySet()){
				try {
					Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(L.get(key).toString(), 6790 + key));
					if(this.controller.getUidmap().get(key).type == 0){
						UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.class, cand);
						uproxy.Election();
					} else{
						FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.class, cand);
						fproxy.Election();
					}
					return;
				} catch (IOException e) {
					number_of_failures++;
				}
			}
			//no one is reachable, choose yourself
			try {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6790 + ID));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
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

	@Override
	public int Election() throws AvroRemoteException {
		if(!this.participant){
			this.startLeaderElection();
		}
		return 0;
	}
	
	@Override
	public int ReceiveCoord(CharSequence server_ip) throws AvroRemoteException {
		this.server_ip_address = server_ip.toString();
		try {
			fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, fridge);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("New leader: " + server_ip);
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
	}
}
