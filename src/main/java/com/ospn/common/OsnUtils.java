package com.ospn.common;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Random;

public class OsnUtils {
    public static BufferedOutputStream mLogger;
    public static BufferedOutputStream mStack;
    public static final Object mLock = new Object();

    public static void init(String logFileName){
        try {
            mLogger = new BufferedOutputStream(new FileOutputStream(logFileName+".log"));
            mStack = new BufferedOutputStream(new FileOutputStream("stack-"+logFileName+".log"));
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private static String getStack(int level){
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        return Thread.currentThread().getId() +
                " " + stackTrace[level].getClassName() +
                "." + stackTrace[level].getMethodName() +
                "." + stackTrace[level].getLineNumber();
    }
    private static void writeStk(String data){
        try{
            synchronized (mLock){
                mStack.write(data.getBytes());
                mStack.write("\r\n".getBytes());
                mStack.flush();
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private static void writeLog(BufferedOutputStream logger, String data){
        try{
            SimpleDateFormat formatter = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss] ");
            Date date = new Date(System.currentTimeMillis());
            String time = formatter.format(date);
            synchronized (mLock) {
                logger.write(time.getBytes());
                logger.write(data.getBytes());
                logger.write("\r\n".getBytes());
                logger.flush();
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void logError(Throwable e){
        String info = e.toString();
        String trace = getStack(3);
        writeLog(mLogger, "[ "+trace+" E] " + info);
        writeLog(mStack, "[ "+trace+" E] " + info);
        StackTraceElement[] stackTraceElements = e.getStackTrace();
        for(StackTraceElement s : stackTraceElements)
            writeStk(s.toString());
    }
    public static void logError(String error){
        String trace = getStack(3);
        writeLog(mLogger, "[ "+trace+" E] " + error);
    }
    public static void logInfo(String info){
        String trace = getStack(3);
        writeLog(mLogger, "[ "+trace+" I] " + info);
    }
    static public void logInfo3(String info){
        String trace = getStack(4);
        writeLog(mLogger, "[ "+trace+" I] " + info);
    }
    public static byte[] sha256(byte[] data){
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(data);
            data = messageDigest.digest();
        } catch (Exception e){
            OsnUtils.logError(e);
        }
        return data;
    }
    public static String aesEncrypt(byte[] data, byte[] key){
        try {
            byte[] iv = new byte[16];
            Arrays.fill(iv, (byte) 0);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encData = cipher.doFinal(data);
            return Base64.getEncoder().encodeToString(encData);
        }
        catch (Exception e){
            OsnUtils.logError(e);
        }
        return null;
    }
    public static String aesEncrypt(String data, String key){
        byte[] pwdHash = sha256(key.getBytes());
        return aesEncrypt(data.getBytes(), pwdHash);
    }
    public static byte[] aesDecrypt(byte[] data, byte[] key){
        try {
            byte[] iv = new byte[16];
            Arrays.fill(iv,(byte)0);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        }
        catch (Exception e){
            OsnUtils.logError(e);
        }
        return null;
    }
    public static byte[] aesDecrypt(String data, byte[] key){
        byte[] decData = Base64.getDecoder().decode(data);
        return aesDecrypt(decData, key);
    }
    public static String aesDecrypt(String data, String key){
        byte[] pwdHash = sha256(key.getBytes());
        byte[] decData = Base64.getDecoder().decode(data);
        decData = aesDecrypt(decData, pwdHash);
        if(decData == null)
            return null;
        return new String(decData);
    }
    public static byte[] getAesKey(){
        byte[] key = new byte[32];
        Random random = new Random();
        for(int i = 0; i < 32; ++i)
            key[i] = (byte)random.nextInt(256);
        return key;
    }

    public static String toHexString(byte[] b) {
        StringBuilder hs = new StringBuilder();
        for (byte value : b) {
            String hex = Integer.toHexString(value & 0xFF);
            if (hex.length() == 1)
                hex = '0' + hex;
            hs.append(hex);
        }
        return hs.toString();
    }

}
