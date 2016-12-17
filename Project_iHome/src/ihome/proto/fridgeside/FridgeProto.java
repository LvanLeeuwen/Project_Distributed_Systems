/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package ihome.proto.fridgeside;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public interface FridgeProto {
  public static final org.apache.avro.Protocol PROTOCOL = org.apache.avro.Protocol.parse("{\"protocol\":\"FridgeProto\",\"namespace\":\"ihome.proto.fridgeside\",\"types\":[],\"messages\":{\"send_current_items\":{\"request\":[],\"response\":\"string\"},\"send_all_items\":{\"request\":[],\"response\":\"string\"},\"update_controller\":{\"request\":[{\"name\":\"jsonController\",\"type\":\"string\"}],\"response\":\"string\"},\"add_item\":{\"request\":[{\"name\":\"item\",\"type\":\"string\"}],\"response\":\"string\"},\"remove_item\":{\"request\":[{\"name\":\"item\",\"type\":\"string\"}],\"response\":\"string\"},\"ReceiveCoord\":{\"request\":[{\"name\":\"server_ip\",\"type\":\"string\"},{\"name\":\"port\",\"type\":\"int\"}],\"response\":\"int\"},\"receiveElection\":{\"request\":[{\"name\":\"receivedID\",\"type\":\"int\"}],\"response\":\"string\"},\"receiveElected\":{\"request\":[{\"name\":\"serverIP\",\"type\":\"string\"},{\"name\":\"port\",\"type\":\"int\"},{\"name\":\"serverID\",\"type\":\"int\"}],\"response\":\"string\"},\"getLeader\":{\"request\":[],\"response\":\"string\"}}}");
  java.lang.CharSequence send_current_items() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence send_all_items() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence update_controller(java.lang.CharSequence jsonController) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence add_item(java.lang.CharSequence item) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence remove_item(java.lang.CharSequence item) throws org.apache.avro.AvroRemoteException;
  int ReceiveCoord(java.lang.CharSequence server_ip, int port) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence receiveElection(int receivedID) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence receiveElected(java.lang.CharSequence serverIP, int port, int serverID) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence getLeader() throws org.apache.avro.AvroRemoteException;

  @SuppressWarnings("all")
  public interface Callback extends FridgeProto {
    public static final org.apache.avro.Protocol PROTOCOL = ihome.proto.fridgeside.FridgeProto.PROTOCOL;
    void send_current_items(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void send_all_items(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void update_controller(java.lang.CharSequence jsonController, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void add_item(java.lang.CharSequence item, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void remove_item(java.lang.CharSequence item, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void ReceiveCoord(java.lang.CharSequence server_ip, int port, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void receiveElection(int receivedID, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void receiveElected(java.lang.CharSequence serverIP, int port, int serverID, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void getLeader(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
  }
}