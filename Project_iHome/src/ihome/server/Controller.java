package ihome.server;

import ihome.proto.serverside.ServerProto;
import ihome.proto.lightside.LightProto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;


public class Controller implements ServerProto 
{
	private static Server server = null;
	private Map<Integer, Device> uidmap = new HashMap<Integer, Device>();
	private Map<Integer, ArrayList<Float>> sensormap = new HashMap<Integer, ArrayList<Float>>();
	private int nextID = 0;
	private final int nr_types = 4;

	@Override
	public CharSequence connect(int device_type) throws AvroRemoteException {
		
		if(device_type < 0 || device_type >= nr_types)
		{
			return "{\"UID\" : NULL, \"Error\" : \"[Error] No such type defined.\"}";
		}
		
		try{
			uidmap.put(nextID, new Device(device_type));
			if(device_type == 1)
				sensormap.put(nextID, new ArrayList<Float>());
			System.out.println("Device connected with id " + nextID);
			System.out.println("uidmap size: " + uidmap.size());
			printInSession();
			return "{\"UID\" : \""+ (nextID++) + "\", \"Error\" : NULL}";
		}catch(Exception e){
			return "{\"UID\" : NULL, \"Error\" : \"[Error] " + e.getMessage();
		}
	}

	@Override
	public CharSequence disconnect(int uid) throws AvroRemoteException {
		System.out.println("disconnect");
		if(!uidmap.containsKey(uid))
		{
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		try{
			uidmap.remove(uid);
			return "{\"Error\" : NULL}";
		}catch(Exception e){
			return "{\"Error\" : \"[Error] " + e.getMessage();
		}
		
	}
	
	@Override
	public CharSequence update_temperature(int uid, float value) throws AvroRemoteException {
		if(!uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		else if(!sensormap.containsKey(uid)){
			return "{\"Error\" : \"[Error] Device with uid " + uid +  " is not heat sensor.\"}";
		}
		try{
			this.sensormap.get(uid).add(value);
			return "{\"Error\" : NULL}";

		}catch (Exception e){

		}
		return null;
	}

	

	@Override
	public CharSequence get_temperature_list(int uid, int sensor_id) throws AvroRemoteException {
		if(!uidmap.containsKey(sensor_id)) {
			return "{\"Error\" : \"[Error] sensor_id not found in current session.\"}";
		}
		else if(!uidmap.containsKey(uid)){
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		else if(!sensormap.containsKey(sensor_id)){
			return "{\"Error\" : \"[Error] Device with uid " + uid +  " is not heat sensor.\"}";
		}
		try{
			return this.sensormap.get(sensor_id).toString();

		}catch (Exception e){

		}
		return null;
	}

	@Override
	public CharSequence get_temperature_current(int uid) throws AvroRemoteException {
		if(!uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		
		try{
			double mean = 0.0;
			int n = 0;
			for(ArrayList<Float>  c : this.sensormap.values()){
				n++;
				mean += c.get(c.size() - 1);
			}
			return Double.toString(mean/n);

		}catch (Exception e){

		}
		return null;
	}
	
	
	public void printInSession(){
		System.out.println("Currently in session("+ this.uidmap.size()+ "):");
		for(int id : uidmap.keySet())
		{
			System.out.print(id + " ");
		}
		System.out.print("\n");
	}

	public void runServer(){
		try
		{
			server = new SaslSocketServer(new SpecificResponder(ServerProto.class,
					this), new InetSocketAddress(6789));
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
	
	public void get_light_state(int uid) {
		Device light = uidmap.get(uid);
		if (light.type != 3) {
			// TODO error
			return;
		}
		try {
			Transceiver trans = new SaslSocketTransceiver(new InetSocketAddress(6790+uid));
			LightProto proxy = SpecificRequestor.getClient(LightProto.class, trans);
			CharSequence state = proxy.send_state();
			System.out.println(state);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String [] args){
		Controller controller = new Controller();
		controller.runServer();

		while(true){
			Scanner reader = new Scanner(System.in);
			System.out.println("What do you want to do?");
			System.out.println("1) Get in-session list");
			System.out.println("2) Get state light");
			
			int in = reader.nextInt();
			if(in == 1){
				controller.printInSession();
				
			}
			/*
			else if(in ==2){
				System.out.println("Give id:");
				int id = reader.nextInt();
				controller.get_light_state(id);
			}
			*/
			else{
				break;
			}
		}

		//controller.get_light_state(0);
		controller.stopServer();
	}
}
