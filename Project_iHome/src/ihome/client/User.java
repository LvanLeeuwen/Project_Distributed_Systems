package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import org.json.*;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;

public class User {
	
	private Controller controller = new Controller();
	private Transceiver user;
	private ServerProto proxy;
	
	private String name;
	private int nextName = 0;
	private int ID;
	
	public void connect_to_server() {
		try {
			user = new SaslSocketTransceiver(new InetSocketAddress(6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(0);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "user" + nextName++;
			System.out.println("username: " + name + " ID: " + ID + " Entered the house");
		} catch (Exception e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
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

	public static void main(String[] args) {
		// Connect to server
		User myUser = new User();
		myUser.connect_to_server();
		while (true) {
			// execute actions from command line
			
			/*
			 * Possible actions:
			 * 		Enter the system (connect to server)
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
		}
	}

}
