package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Scanner;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.json.JSONObject;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;
import ihome.proto.lightside.LightProto;

public class Light implements LightProto {
	
	private Server server = null;
	private Transceiver light;
	private ServerProto proxy;

	// Light variables
	private String name;
	private int ID;
	private String state = "off";
	private String IPAddress;
	private String server_ip_address;
	
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
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(3, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "light" + ID;
			System.out.println("name: " + name + " ID: " + ID);
		} catch (Exception e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
	
	@Override
	public CharSequence send_state() throws AvroRemoteException {
		return "{\"state\" : \"" + state + "\"}";
	}

	@Override
	public CharSequence switch_state() throws AvroRemoteException {
		if (state == "on") {
			state = "off";
			System.out.println("Light " + name + " with ID " + ID + " is switched off.");
		} else {
			state = "on";
			System.out.println("Light " + name + " with ID " + ID + " is switched on.");
		}
		return "{\"Error\" : NULL}";
	}
	
	public void runServer() {
		try
		{
			server = new SaslSocketServer(new SpecificResponder(LightProto.class,
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
	
	@Override
	public int ReceiveCoord(CharSequence server_ip) throws AvroRemoteException {
		this.server_ip_address = server_ip.toString();
		try {
			light = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, light);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("New leader: " + server_ip);
		return 0;
	}
	
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
		
		while(true){
			int a;
		}
		
		//myLight.stopServer();
	}
}
