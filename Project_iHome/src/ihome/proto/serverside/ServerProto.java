/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package ihome.proto.serverside;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public interface ServerProto {
  public static final org.apache.avro.Protocol PROTOCOL = org.apache.avro.Protocol.parse("{\"protocol\":\"ServerProto\",\"namespace\":\"ihome.proto.serverside\",\"types\":[],\"messages\":{\"connect\":{\"request\":[{\"name\":\"device_type\",\"type\":\"int\"},{\"name\":\"ip_address\",\"type\":\"string\"}],\"response\":\"string\"},\"disconnect\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"string\"},\"get_all_devices\":{\"request\":[],\"response\":\"string\"},\"update_temperature\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"},{\"name\":\"value\",\"type\":\"float\"}],\"response\":\"string\"},\"get_temperature_list\":{\"request\":[],\"response\":\"string\"},\"get_temperature_current\":{\"request\":[],\"response\":\"string\"},\"get_lights_state\":{\"request\":[],\"response\":\"string\"},\"switch_state_light\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"string\"},\"get_fridge_contents\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"string\"},\"i_am_alive\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"int\"},\"get_fridge_port\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"},{\"name\":\"fridgeid\",\"type\":\"int\"}],\"response\":\"string\"},\"release_fridge\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"int\"},\"report_offline\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"int\"},\"notify_empty_fridge\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"int\"},\"sendController\":{\"request\":[],\"response\":\"int\"},\"user_enters\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"string\"},\"user_leaves\":{\"request\":[{\"name\":\"uid\",\"type\":\"int\"}],\"response\":\"string\"},\"updateController\":{\"request\":[{\"name\":\"jsonController\",\"type\":\"string\"}],\"response\":\"string\"},\"sendCoord\":{\"request\":[],\"response\":\"string\"},\"getIDdevice\":{\"request\":[{\"name\":\"devicetype\",\"type\":\"int\"}],\"response\":\"string\"}}}");
  java.lang.CharSequence connect(int device_type, java.lang.CharSequence ip_address) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence disconnect(int uid) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence get_all_devices() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence update_temperature(int uid, float value) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence get_temperature_list() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence get_temperature_current() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence get_lights_state() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence switch_state_light(int uid) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence get_fridge_contents(int uid) throws org.apache.avro.AvroRemoteException;
  int i_am_alive(int uid) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence get_fridge_port(int uid, int fridgeid) throws org.apache.avro.AvroRemoteException;
  int release_fridge(int uid) throws org.apache.avro.AvroRemoteException;
  int report_offline(int uid) throws org.apache.avro.AvroRemoteException;
  int notify_empty_fridge(int uid) throws org.apache.avro.AvroRemoteException;
  int sendController() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence user_enters(int uid) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence user_leaves(int uid) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence updateController(java.lang.CharSequence jsonController) throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence sendCoord() throws org.apache.avro.AvroRemoteException;
  java.lang.CharSequence getIDdevice(int devicetype) throws org.apache.avro.AvroRemoteException;

  @SuppressWarnings("all")
  public interface Callback extends ServerProto {
    public static final org.apache.avro.Protocol PROTOCOL = ihome.proto.serverside.ServerProto.PROTOCOL;
    void connect(int device_type, java.lang.CharSequence ip_address, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void disconnect(int uid, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void get_all_devices(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void update_temperature(int uid, float value, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void get_temperature_list(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void get_temperature_current(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void get_lights_state(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void switch_state_light(int uid, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void get_fridge_contents(int uid, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void i_am_alive(int uid, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void get_fridge_port(int uid, int fridgeid, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void release_fridge(int uid, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void report_offline(int uid, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void notify_empty_fridge(int uid, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void sendController(org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
    void user_enters(int uid, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void user_leaves(int uid, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void updateController(java.lang.CharSequence jsonController, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void sendCoord(org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void getIDdevice(int devicetype, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
  }
}