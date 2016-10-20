package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.json.JSONObject;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;

public class Fridge {
	
	private Controller controller = new Controller();
	private static Transceiver fridge;
	private static ServerProto proxy;

	private String name;
	private static int nextName = 0;
	private int ID;
	private boolean opened = false;
	private ArrayList<String> items = new ArrayList<String>();
	
	public static void connect_to_server() {
		try {
			fridge = new SaslSocketTransceiver(new InetSocketAddress(6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
			System.out.println("Connected to server");
		} catch (IOException e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
	
	public void add_to_house() {
		try {
			CharSequence response = proxy.connect(2);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "fridge" + nextName++;
			System.out.println("name: " + name + " ID: " + ID);
		} catch (Exception e){
			System.out.println(e.getMessage());
		}
	}
	
	public void open() {
		opened = true;
	}
	
	public void close() {
		opened = false;
	}
	
	public void print_items() {
		for (String item : items) {
			System.out.println(item);
		}
	}
	
	public ArrayList<String> get_items() {
		return items;
	}
	
	public void add_item(String name) {
		if (items.contains(name)) {
			name += "0";
		}
		items.add(name);
	}
	
	public void remove_item(String name) {
		items.remove(name);
		if (items.isEmpty()) {
			/* 
			 * Notify controller that fridge is empty.
			 * Controller should notify all users that fridge is empty.
			 */
		}
	}
	
	public static void main(String[] args) {
		// Connect to server
		Fridge.connect_to_server();
		Fridge myFridge = new Fridge();
		myFridge.add_to_house();
		while (true) {
			// execute actions from command line
			/*
			 * Possible actions:
			 * 		Print a list of items stored in the fridge.
			 * 		Return a list of all items stored in the fridge.
			 * 		Add or remove an item to or from the fridge.
			 */
		}
	}

}
