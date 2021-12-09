package com.ospn.common;

import java.util.Base64;

import static com.ospn.common.OsnUtils.logError;

public class ECUtils {
    private static native byte[] ecSignSSL(byte[] priKey, byte[] data);
    private static native boolean ecVerifySSL(byte[] pubKey, byte[] data, byte[] sign);
    private static native byte[] ecIESEncryptSSL(byte[] pubKey, byte[] data);
    private static native byte[] ecIESDecryptSSL(byte[] priKey, byte[] data);
    private static native byte[] createECKey();
    private static native byte[] getECPublicKey(byte[] priKey);
    public static native String b58Encode(byte[] data);
    public static native byte[] b58Decode(String data);

    static {
        System.loadLibrary("ecSSL");
    }

    private static byte[] toPublicKey(String osnID){
        if(!osnID.startsWith("OSN"))
            return null;
        String pubKey = osnID.substring(4);
        byte[] pKey = b58Decode(pubKey);
        byte[] rKey = new byte[pKey.length-2];
        System.arraycopy(pKey, 2, rKey, 0, pKey.length-2);
        return rKey;
    }
    private static byte[] toPrivateKey(String osnID){
        String priKey = osnID.substring(3);
        return Base64.getDecoder().decode(priKey);
    }

    public static String osnHash(byte[] data){
        byte[] hash = OsnUtils.sha256(data);
        return Base64.getEncoder().encodeToString(hash);
    }
    public static String osnSign(String priKey, byte[] data){
        try {
            byte[] pKey = toPrivateKey(priKey);
            byte[] sign = ecSignSSL(pKey, data);
            return sign == null ? null : Base64.getEncoder().encodeToString(sign);
        }
        catch (Exception e){
            logError(e);
        }
        return null;
    }
    public static boolean osnVerify(String osnID, byte[] data, String sign){
        try {
            byte[] signData = Base64.getDecoder().decode(sign);
            byte[] pKey = toPublicKey(osnID);
            return pKey != null && ecVerifySSL(pKey, data, signData);
        }
        catch (Exception e){
            logError(e);
        }
        return false;
    }
    public static byte[] ecIESEncrypt(String osnID, byte[] data){
        byte[] pKey = toPublicKey(osnID);
        return pKey == null ? null : ecIESEncryptSSL(pKey, data);
    }
    public static byte[] ecIESDecrypt(String priKey, byte[] data){
        byte[] pKey = toPrivateKey(priKey);
        return ecIESDecryptSSL(pKey, data);
    }
    public static String[] createOsnID(String type){
        try {
            byte[] priKey = createECKey();
            byte[] pubKey = getECPublicKey(priKey);

            byte[] address = new byte[1 + 1 + pubKey.length]; //version(1)|flag(1)|pubkey(33)
            address[0] = 1;
            address[1] = 0;
            String osnType = "OSNU";
            if (type.equalsIgnoreCase("group")) {
                address[1] = 1;
                osnType = "OSNG";
            }
            else if (type.equalsIgnoreCase("service")) {
                address[1] = 2;
                osnType = "OSNS";
            }
            System.arraycopy(pubKey, 0, address, 2, pubKey.length);
            String addrString = osnType + b58Encode(address);
            String priKeys = "VK0"+Base64.getEncoder().encodeToString(priKey);

            return new String[]{addrString, priKeys};
        }
        catch (Exception e){
            logError(e);
        }
        return null;
    }
    public static String ecEncrypt2(String osnID, byte[] data){
        byte[] encData = ecIESEncrypt(osnID, data);
        return Base64.getEncoder().encodeToString(encData);
    }
    public static byte[] ecDecrypt2(String priKey, String data){
        return ecIESDecrypt(priKey, Base64.getDecoder().decode(data));
    }
}
