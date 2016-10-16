package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import ihome.proto.serverside.ServerProto;
import ihome.server.Controller;

public class Fridge {
	
	private Controller controller = new Controller();

	public static void main(String[] args) {
		try {
			// connect to server
			Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6789));
			ServerProto proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, user);
		} catch (IOException e) {
			System.err.println("[error] failed to connect to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

}
