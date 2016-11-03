package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.CallFuture;
import org.json.JSONObject;

import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;

public class TemperatureSensor {

	// Variables to set up a connection with the server.
	private Transceiver sensor;
	private ServerProto.Callback proxy;
	private CallFuture<CharSequence> future = new CallFuture<CharSequence>();
	
	// Variables specifically for the temperature sensor
	private int ID;
	private String name;
	private float temperature;
	private ArrayList<Float> unsendTemperatures = new ArrayList<Float>();
	
	public void connect_to_server(float initTemp) {
		try {
			sensor = new SaslSocketTransceiver(new InetSocketAddress(6789));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, sensor);
			System.out.println("Connected to server");
			CharSequence response = proxy.connect(1);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			ID = json.getInt("UID");
			name = "sensor" + ID;
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
			if (unsendTemperatures.isEmpty()) {
				proxy.update_temperature(ID, temperature, future);
				JSONObject json = new JSONObject(future.get().toString());
				if (!json.isNull("Error")) {
					CharSequence error = json.getString("Error");
					System.out.println("Error: " + error);
				} else {
					System.out.println("Verzonden");
				}
			} else {
				unsendTemperatures.add(temperature);
				for (float temp : unsendTemperatures) {
					proxy.update_temperature(ID, temp, future);
					JSONObject json = new JSONObject(future.get().toString());
					if (!json.isNull("Error")) {
						CharSequence error = json.getString("Error");
						System.out.println("Error: " + error);
					} else {
						System.out.println("Verzonden unsend");
					}
				}
			}
			
		} catch (IOException e) {
			System.out.println("IOException");
		} catch (InterruptedException e) {
			System.out.println("InterruptedException");
		} catch (ExecutionException e) {
			if (unsendTemperatures.isEmpty()) {
				unsendTemperatures.add(temperature);
				System.out.println("Toegevoegd aan unsend");
			} else {
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("Exception");
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
