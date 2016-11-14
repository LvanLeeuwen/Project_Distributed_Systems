package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;

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

	private String name;
	private int nextName = 0;
	private int ID;
	private String state = "off";
	
	public void connect_to_server() {
		try {
			light = new SaslSocketTransceiver(new InetSocketAddress(6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, light);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(3);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "light" + nextName++;
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
	
	public static void main(String[] args) {
		// Connect to server
		Light myLight = new Light();
		myLight.connect_to_server();
		
		myLight.runServer();
		while(true){
			int a;
		}
		
		//myLight.stopServer();
	}

}
