package com.ospn.server;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ospn.OsnConnector;
import com.ospn.common.ECUtils;
import com.ospn.common.OsnUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static com.ospn.OsnConnector.*;
import static com.ospn.common.OsnUtils.logInfo;

public class OsnFindID {
    public static void initServer(){
        new Thread(OsnFindID::findTask).start();
    }
    private static void findTask(){
        while(true){
            try {
                long timeBase = System.currentTimeMillis();
                synchronized (queryLock){
                    queryLock.wait(5000);
                }
                if(osnQueryMap.isEmpty())
                    continue;
                long timeDiff = System.currentTimeMillis() - timeBase;
                if(timeDiff < 2000)
                    Thread.sleep(2000-timeDiff);

                Collection<OsnConnector.QueryData> queryData = osnQueryMap.values();
                for(OsnConnector.QueryData q:queryData){
                    if(timeBase - q.timeStamp > 60*1000) {
                        logInfo("query osnID timeout: "+q.osnID);
                        osnQueryMap.remove(q.osnID);
                    }
                }

                if(!osnQueryMap.isEmpty()){
                    JSONArray targetList = new JSONArray();
                    targetList.addAll(osnQueryMap.keySet());

                    JSONObject findOsnID = new JSONObject();
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    findOsnID.put("command", "findOsnID");
                    findOsnID.put("targetList", targetList);
                    findOsnID.put("timeStamp", timestamp);
                    findOsnID.put("querySign", ECUtils.osnSign(osnSrvKey, timestamp.getBytes(StandardCharsets.UTF_8)));
                    findOsnID.put("ip", myIP);

                    OsnUtils.logInfo("find targetList: "+ targetList);

                    String[] nodes = osnPeer.isEmpty()?cfgPeer.toArray(new String[1]):osnPeer.toArray(new String[1]);
                    for(String ip:nodes) {
                        if (Inst.isMyNode(ip))
                            continue;
                        if(!Inst.pushJson(ip, findOsnID)){
                            synchronized (syncLock){
                                syncLock.notify();
                            }
                        }
                    }
                }
            }
            catch (Exception e){
                OsnUtils.logInfo(e.toString());
            }
        }
    }
}
