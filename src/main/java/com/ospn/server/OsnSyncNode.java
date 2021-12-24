package com.ospn.server;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.ospn.OsnConnector.*;
import static com.ospn.common.OsnUtils.logError;
import static com.ospn.common.OsnUtils.logInfo;

public class OsnSyncNode {
    public static void initServer(){
        new Thread(OsnSyncNode::syncTask).start();
    }
    public static JSONObject getSyncInfo(boolean isCommand){
        Set<String> syncPeer = new HashSet<>();
        syncPeer.addAll(osnPeer);
        syncPeer.addAll(cfgPeer);  //防止掉线太长，无法再接入网络
        syncPeer.add(myIP);
        JSONArray nodeList = new JSONArray();
        nodeList.addAll(syncPeer);
        JSONObject json = new JSONObject();
        if(isCommand)
            json.put("command", "syncNode");
        json.put("nodeList", nodeList);
        json.put("syncKey", osnSyncKey);
        json.put("imType", imType);
        json.put("ip",myIP);
        return json;
    }
    public static void setPeerInfo(JSONObject json){
        String ip = json.getString("ip");
        if(ip == null)
            return;
        PeerInfo info = osnPeerInfo.computeIfAbsent(ip, k->new PeerInfo());
        info.ip = ip;
        info.imType = json.getString("imType");
        info.syncKey = json.getString("syncKey");
    }
    private static void syncTask(){
        while(true) {
            try {
                synchronized (syncLock){
                    syncLock.wait(5*60*1000);
                }
                JSONObject peerNode = getSyncInfo(true);
                List<String> addNode = new ArrayList<>();
                List<String> delNode = new ArrayList<>();

                boolean flagNewNode = false;
                Object[] nodes = osnPeer.toArray();
                while(nodes.length != 0) {
                    addNode.clear();
                    for (Object node : nodes) {
                        String ip = (String) node;
                        if(Inst.isMyNode(ip))
                            continue;
                        JSONObject json = Inst.pullJson(ip,peerNode);
                        if (json == null) {
                            osnPeer.remove(ip);
                            delNode.add(ip);
                            logInfo("remove ospn node: " + ip);
                            continue;
                        }
                        setPeerInfo(json);
                        if (json.containsKey("nodeList")) {
                            JSONArray array = json.getJSONArray("nodeList");
                            for (Object o : array) {
                                ip = (String) o;
                                if (!Inst.isContain(ip) && !delNode.contains(ip)) {
                                    osnPeer.add(ip);
                                    addNode.add(ip);
                                    flagNewNode = true;
                                    logInfo("new ospn node: " + ip);
                                }
                            }
                        }
                    }
                    nodes = addNode.toArray();
                }
                if(flagNewNode){
                    synchronized (queryLock){
                        queryLock.notify();
                    }
                }
            } catch (Exception e) {
                logError(e);
            }
        }
    }
}
