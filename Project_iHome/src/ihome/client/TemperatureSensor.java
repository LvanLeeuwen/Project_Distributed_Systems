package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.json.JSONObject;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;

public class TemperatureSensor {

	private Controller controller = new Controller();
	private Transceiver sensor;
	private ServerProto proxy;

	private String name;
	private int nextName = 0;
	private int ID;
	private double temperature;
	
	public void connect_to_server(double initTemp) {
		try {
			sensor = new SaslSocketTransceiver(new InetSocketAddress(6789));
			proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, sensor);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(1);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "sensor" + nextName++;
			temperature = initTemp;
			System.out.println("name: " + name + " ID: " + ID);
		} catch (Exception e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
	
	public static void main(String[] args) {
		// Connect to server
		TemperatureSensor mySensor = new TemperatureSensor();
		mySensor.connect_to_server(18);
		while (true) {
			// execute actions from command line
			/*
			 * Possible actions:
			 * 		
			 */
		}
	}

}
