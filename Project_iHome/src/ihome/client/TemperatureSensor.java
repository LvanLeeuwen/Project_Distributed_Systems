package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.CallFuture;
import org.json.JSONObject;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;

public class TemperatureSensor {

	private Controller controller = new Controller();
	private Transceiver sensor;
	//private ServerProto proxy;
	private ServerProto.Callback proxy;
	private CallFuture<CharSequence> future = new CallFuture<CharSequence>();
	
	private String name;
	private int nextName = 0;
	private int ID;
	private float temperature;
	
	public void connect_to_server(float initTemp) {
		try {
			/*
			Hello.Callback proxy = SpecificRequestor.getClient(Hello.Callback.class, client);
			CallFuture<CharSequence> future = new CallFuture<CharSequence>();
			proxy.sayHello("Bob", future);
			System.out.println(future.get());
			client.close();
			*/
			
			sensor = new SaslSocketTransceiver(new InetSocketAddress(6789));
			//proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, sensor);
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, sensor);
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
	
	public void sent_temperature() {
		float rangeMin = -1.0f;
		float rangeMax = 1.0f;
		Random r = new Random();
		float value = rangeMin + (rangeMax - rangeMin) * r.nextFloat();
		temperature += value;
		try {
			proxy.update_temperature(ID, temperature, future);
			//System.out.println(future.get());
			//System.out.println(response);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		// Connect to server
		TemperatureSensor mySensor = new TemperatureSensor();
		mySensor.connect_to_server(18f);
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				mySensor.sent_temperature();
			}
		};
		timer.schedule(task, 10000, 10000);
		
		while (true) {
			// execute actions from command line
			/*
			 * Possible actions:
			 * 		
			 */
		}
	}

}
