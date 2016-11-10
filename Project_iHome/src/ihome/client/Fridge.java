package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.ipc.Server;
import org.json.JSONObject;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.lightside.LightProto;

public class Fridge implements FridgeProto {
	
	private Server server = null;
	private Controller controller = new Controller();
	private Transceiver fridge;
	private ServerProto proxy;

	private String name;
	private int nextName = 0;
	private int ID;
	private boolean opened = false;
	private ArrayList<String> items = new ArrayList<String>();
	private ArrayList<String> allItems = new ArrayList<String>();
	
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server() {
		try {
			fridge = new SaslSocketTransceiver(new InetSocketAddress(6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(2);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "fridge" + nextName++;
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
					this), new InetSocketAddress(6790+ID));
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
	
	@Override
	public CharSequence send_current_items() throws AvroRemoteException {
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
		controller.updateController(jsonController);
		return "";
	}
	
	/**************************
	 ** MAIN FUNCTIONALITY   **
	 **************************/
	public static void main(String[] args) {
		// Connect to server
		Fridge myFridge = new Fridge();
		myFridge.connect_to_server();
		
		myFridge.runServer();
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
