/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package ihome.proto.lightside;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public interface LightProto {
  public static final org.apache.avro.Protocol PROTOCOL = org.apache.avro.Protocol.parse("{\"protocol\":\"LightProto\",\"namespace\":\"ihome.proto.lightside\",\"types\":[],\"messages\":{\"send_state\":{\"request\":[],\"response\":\"string\"},\"switch_state\":{\"request\":[],\"response\":\"string\"},\"ReceiveCoord\":{\"request\":[{\"name\":\"server_ip\",\"type\":\"string\"},{\"name\":\"port\",\"type\":\"int\"}],\"response\":\"int\"},\"turn_off\":{\"request\":[],\"response\":\"int\"},\"turn_back\":{\"request\":[],\"response\":\"int\"},\"receiveElection\":{\"request\":[{\"name\":\"receivedID\",\"type\":\"int\"}],\"response\":\"string\"},\"receiveElected\":{\"request\":[{\"name\":\"serverIP\",\"type\":\"string\"},{\"name\":\"port\",\"type\":\"int\"},{\"name\":\"serverID\",\"type\":\"int\"}],\"response\":\"string\"},\"update_uidmap\":{\"request\":[{\"name\":\"json_uidmap\",\"type\":\"string\"}],\"response\":\"string\"},\"getLeader\":{\"request\":[],\"response\":\"string\"}}}");
  java.lang.CharSequence send_state() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence switch_state() throws org.apache.avro.AvroRemoteException;
  int ReceiveCoord(java.lang.CharSequence server_ip, int port) throws org.apache.avro.AvroRemoteException;
  int turn_off() throws org.apache.avro.AvroRemoteException;
  int turn_back() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence receiveElection(int receivedID) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence receiveElected(java.lang.CharSequence serverIP, int port, int serverID) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence update_uidmap(java.lang.CharSequence json_uidmap) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence getLeader() throws org.apache.avro.AvroRemoteException;

  @SuppressWarnings("all")
  public interface Callback extends LightProto {
    public static final org.apache.avro.Protocol PROTOCOL = ihome.proto.lightside.LightProto.PROTOCOL;
    void send_state(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void switch_state(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void ReceiveCoord(java.lang.CharSequence server_ip, int port, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void turn_off(org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void turn_back(org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void receiveElection(int receivedID, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void receiveElected(java.lang.CharSequence serverIP, int port, int serverID, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void update_uidmap(java.lang.CharSequence json_uidmap, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void getLeader(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
  }
}