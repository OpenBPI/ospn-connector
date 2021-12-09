package com.ospn.server;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ospn.OsnConnector;
import com.ospn.data.SyncIDData;

import java.util.List;

import static com.ospn.OsnConnector.*;
import static com.ospn.common.OsnUtils.logError;
import static com.ospn.common.OsnUtils.logInfo;

public class OsnIDSync {
    private static JSONObject syncJson;
    private static final Object syncLock = new Object();

    public static void syncWorker() {
        try {
            for (String peer : cfgPeer) {
                logInfo("start sync from: "+peer);
                String syncOsnID = db.getPeerSyncOsnID(peer);
                while (true) {
                    JSONObject json = new JSONObject();
                    json.put("command", "syncOsnID");
                    json.put("osnID", syncOsnID);
                    json.put("sync", "id");
                    json.put("ip", myIP);

                    Inst.sendOsxJson(peer, json);

                    syncJson = null;
                    synchronized (syncLock){
                        syncLock.wait(10000);
                    }
                    if(syncJson == null){
                        logInfo("sync timeout: "+peer);
                        break;
                    }

                    JSONArray osnIDList = syncJson.getJSONArray("osnIDList");
                    if (osnIDList == null || osnIDList.isEmpty()) {
                        db.setPeerSyncOsnID(peer, syncOsnID);
                        break;
                    }
                    List<SyncIDData> idDataList = osnIDList.toJavaList(SyncIDData.class);
                    if (idDataList != null) {
                        db.setSyncIDDatas(idDataList);
                        SyncIDData newlyID = idDataList.get(idDataList.size() - 1);
                        syncOsnID = newlyID.osnID;
                    }
                }
            }
        }
        catch (Exception e){
            logError(e);
        }
    }
    public static void synxOsnID(JSONObject json){
        syncJson = json;
        synchronized (syncLock){
            syncLock.notify();
        }
    }
    public static void initServer(){
        new Thread(OsnIDSync::syncWorker).start();
    }
}
