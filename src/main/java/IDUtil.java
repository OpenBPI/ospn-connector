import java.security.MessageDigest;

public class IDUtil {
    public static byte[] SHA256(byte[] data){
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(data);
            data = messageDigest.digest();
        } catch (Exception e){
            e.printStackTrace();
        }
        return data;
    }
    public static String GetHash(String osnID){
        //Marshal pb
        byte[] data = new byte[osnID.length()+6];
        data[0] = 0x08;
        data[1] = 0x02;
        data[2] = 0x12;
        data[3] = (byte)osnID.length();
        System.arraycopy(osnID.getBytes(), 0, data, 4, osnID.length());
        data[data.length-2] = 0x18;
        data[data.length-1] = (byte)osnID.length();
        //encode buffer
        byte[] enc = new byte[data.length+2];
        enc[0] = 0x0a;
        enc[1] = (byte)data.length;
        System.arraycopy(data, 0, enc, 2, data.length);
        //sum
        byte[] hash = SHA256(enc);
        //cid
        byte[] key = new byte[hash.length+2];
        System.arraycopy(hash, 0, key, 2, hash.length);
        key[0] = 0x12;
        key[1] = 0x20;
        return Base58.encode(key);
    }
}
