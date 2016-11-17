/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package ihome.proto.userside;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public interface UserProto {
  public static final org.apache.avro.Protocol PROTOCOL = org.apache.avro.Protocol.parse("{\"protocol\":\"UserProto\",\"namespace\":\"ihome.proto.userside\",\"types\":[],\"messages\":{\"update_controller\":{\"request\":[{\"name\":\"jsonController\",\"type\":\"string\"}],\"response\":\"string\"},\"notify_empty_fridge\":{\"request\":[{\"name\":\"fid\",\"type\":\"int\"}],\"response\":\"int\"}}}");
  java.lang.CharSequence update_controller(java.lang.CharSequence jsonController) throws org.apache.avro.AvroRemoteException;
  int notify_empty_fridge(int fid) throws org.apache.avro.AvroRemoteException;

  @SuppressWarnings("all")
  public interface Callback extends UserProto {
    public static final org.apache.avro.Protocol PROTOCOL = ihome.proto.userside.UserProto.PROTOCOL;
    void update_controller(java.lang.CharSequence jsonController, org.apache.avro.ipc.Callback<java.lang.CharSequence> callback) throws java.io.IOException;
    void notify_empty_fridge(int fid, org.apache.avro.ipc.Callback<java.lang.Integer> callback) throws java.io.IOException;
  }
}