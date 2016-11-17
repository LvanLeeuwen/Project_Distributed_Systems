package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
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

import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;
import ihome.proto.userside.UserProto;

public class User implements UserProto {
	
	final static int wtna = Controller.check_alive_interval / 3; 
	
	private Controller controller;
	private Server server = null;
	private Transceiver user;
	private ServerProto proxy;
	
	private String name;
	private int nextName = 0;
	private int ID;
	private String IPAddress;
	private String server_IP_address;
	
	
	private AliveCaller ac;
	
	private Timer timer;
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	// Default constructor
	public User() {}
	// Constructor met IP address
	public User(String ip_address, String server_ip) {
		IPAddress = ip_address;
		server_IP_address = server_ip;
		controller = new Controller(ip_address);
	}
	
	
	public void connect_to_server() {
		try {
			user = new SaslSocketTransceiver(new InetSocketAddress(server_IP_address, 6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(0, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "user" + nextName++;
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
			System.err.println("[error] failed to send I'm alive");
			e.printStackTrace();
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

	public static void main(String[] args) {
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String ip_address = reader.nextLine();
		System.out.println("What is the servers IP address?");
		String server_ip = reader.nextLine();
		User myUser = new User(ip_address, server_ip);
		myUser.runServer();
		myUser.connect_to_server();
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
			System.out.println("1) Get list of all devices and users");
			System.out.println("2) Get overview of the state of all the lights");
			System.out.println("3) Switch state light");
			System.out.println("4) Get contents fridge");
			System.out.println("5) Get current temperature");
			System.out.println("6) Get history of temperature");
			System.out.println("7) Show my controllers devices");
			
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
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					CharSequence result = myUser.proxy.get_temperature_current(id);
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 6) { // Get history of temperature
				System.out.println("Give id:");
				int id = reader.nextInt();
				try {
					CharSequence result = myUser.proxy.get_temperature_list(id, id);
					System.out.println(result);
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (in == 7) {
				try {
					System.out.println(myUser.controller.get_all_devices());
				} catch (AvroRemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				break;
			}
			
			
		}
	}

}
