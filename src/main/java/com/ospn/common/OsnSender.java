package com.ospn.common;

import com.alibaba.fastjson.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ospn.common.OsnUtils.logError;
import static com.ospn.common.OsnUtils.logInfo;

public class OsnSender {
    final Socket mSocket = new Socket();
    public int mMaxQueue;
    public String mIp;
    public int mPort;
    public boolean mCached = false;
    public boolean mNetwork = false;
    public String mTarget = null;
    public String mError = null;
    public Callback mCallback = null;
    final Object mLock = new Object();
    byte[] mHead = new byte[4];
    public long mHeart = System.currentTimeMillis();
    final ConcurrentLinkedQueue<JSONObject> mQueueList = new ConcurrentLinkedQueue<>();
    final ExecutorService mExecutor = Executors.newFixedThreadPool(2);

    public interface Callback{
        void onDisconnect(OsnSender sender, String error);
        void onCacheJson(OsnSender sender, JSONObject json);
        List<JSONObject> onReadCache(OsnSender sender, String target, int count);
    }
    public static OsnSender newInstance(OsnSender sender){
        return newInstance(sender.mIp,sender.mPort,sender.mTarget,sender.mNetwork,sender.mMaxQueue,sender.mCallback,sender.mQueueList);
    }
    public static OsnSender newInstance(String ip, int port, String target, boolean network, int maxQueue, Callback callback, ConcurrentLinkedQueue<JSONObject> queue){
        OsnSender sender = new OsnSender();
        sender.init(ip,port,target,network,maxQueue,callback,queue);
        return sender;
    }
    public void init(String ip, int port, String target, boolean network, int maxQueue, Callback callback,ConcurrentLinkedQueue<JSONObject> queue){
        mIp = ip;
        mPort = port;
        mTarget = target;
        mNetwork = network;
        mMaxQueue = maxQueue;
        mCallback = callback;
        if(queue != null)
            mQueueList.addAll(queue);
        new Thread(this::worker).start();
        new Thread(this::hearts).start();
        new Thread(this::waiter).start();
    }
    private void sender(String data){
        try {
            byte[] dataBytes = data.getBytes();
            synchronized (this) {
                mHead[0] = (byte) ((dataBytes.length >> 24) & 0xff);
                mHead[1] = (byte) ((dataBytes.length >> 16) & 0xff);
                mHead[2] = (byte) ((dataBytes.length >> 8) & 0xff);
                mHead[3] = (byte) (dataBytes.length & 0xff);
                OutputStream outputStream = mSocket.getOutputStream();
                outputStream.write(mHead);
                outputStream.write(dataBytes);
                outputStream.flush();
            }
        }
        catch (Exception ignored){
        }
    }
    private void hearts(){
        try{
            synchronized (mQueueList) {
                mQueueList.wait();
            }
            String heart = "{\"command\":\"Heart\"}";
            while(!mSocket.isClosed()){
                sender(heart);
                synchronized (mLock) {
                    mLock.wait(2000);
                }
            }
        }
        catch (Exception ignored){
        }
    }
    private void worker(){
        try {
            synchronized (mQueueList) {
                mQueueList.wait();
            }
            long tIdle = System.currentTimeMillis();
            while(!mSocket.isClosed()){
                try{
                    synchronized (mQueueList) {
                        mQueueList.wait(1000);
                    }
                    if(mNetwork && System.currentTimeMillis() - tIdle > 60*1000) {
                        mError = "idle timeout";
                        break;
                    }
                    if(System.currentTimeMillis() - mHeart >= 10000) {
                        mError = "heart timeout";
                        break;
                    }
                    if(!mSocket.isConnected())
                        continue;
                    while(mCached || !mQueueList.isEmpty()){
                        JSONObject json = mQueueList.poll();
                        if(json == null){
                            if(!mCached)
                                break;
                            int count = 20;
                            while (mQueueList.size() + count < mMaxQueue) {
                                List<JSONObject> cacheList = mCallback.onReadCache(this,mTarget,count);
                                mQueueList.addAll(cacheList);
                                if(cacheList.isEmpty()) {
                                    mCached = false;
                                    break;
                                }
                            }
                            continue;
                        }
                        mExecutor.execute(()->sender(json.toString()));
                        mHeart = tIdle = System.currentTimeMillis();
                    }
                }
                catch (Exception e){
                    logError(e.toString());
                    mError = e.toString();
                    break;
                }
            }
            synchronized (mSocket){
                if(!mSocket.isClosed())
                    mSocket.close();
            }
        }
        catch (Exception e){
            logError(e);
        }
    }
    private void waiter(){
        try {
            mSocket.connect(new InetSocketAddress(mIp, mPort), 3000);
            synchronized (mQueueList) {
                mQueueList.notifyAll();
            }
            logInfo("connected to ip: "+mIp+", port: "+mPort);

            byte[] head = new byte[4];
            InputStream inputStream = mSocket.getInputStream();

            while(true){
                Arrays.fill(head,(byte)0);
                if(inputStream.read(head) != 4)
                    break;
                int length = ((head[0]&0xff)<<24) | ((head[1]&0xff)<<16) | ((head[2]&0xff)<<8) | head[3]&0xff;
                byte[] data = new byte[length];
                int readed = 0;
                while(readed < length){
                    int len = inputStream.read(data, readed, length - readed);
                    if(len <= 0)
                        break;
                    readed += len;
                }
                if(readed != length)
                    break;
                mHeart = System.currentTimeMillis();
            }
        }
        catch (Exception e){
            if(!mSocket.isClosed())
                mError = e.toString();
        }
        try {
            synchronized (mSocket){
                if(!mSocket.isClosed())
                    mSocket.close();
            }
            synchronized (mQueueList) {
                mQueueList.notifyAll();
            }
            synchronized (mLock){
                mLock.notify();
            }
            mCallback.onDisconnect(this,mError);
        }
        catch (Exception e){
            logError(e);
        }
    }

    public void send(JSONObject json){
        if(mCached || mQueueList.size() > mMaxQueue) {
            mCallback.onCacheJson(this,json);
            return;
        }
        mQueueList.offer(json);
        synchronized (mQueueList) {
            mQueueList.notify();
        }
    }
}
