package com.ospn.server;

import com.alibaba.fastjson.JSONObject;

import java.util.concurrent.ConcurrentLinkedQueue;

import static com.ospn.OsnConnector.Inst;
import static com.ospn.OsnConnector.osnPeer;
import static com.ospn.common.OsnUtils.logError;

public class OsnBroadcast {
    private final static ConcurrentLinkedQueue<JSONObject> mQueueList = new ConcurrentLinkedQueue<>();

    private static void worker(){
        while(true){
            try{
                synchronized (mQueueList){
                    mQueueList.wait();
                }
                while(!mQueueList.isEmpty()){
                    JSONObject json = mQueueList.poll();
                    for(String peer : osnPeer){
                        Inst.sendOsxJson(peer, json);
                    }
                }
            }
            catch (Exception e){
                logError(e);
            }
        }
    }
    public static void initServer(){
        new Thread(OsnBroadcast::worker).start();
    }
    public static void send(JSONObject json){
        mQueueList.offer(json);
        synchronized (mQueueList){
            mQueueList.notify();
        }
    }
}
