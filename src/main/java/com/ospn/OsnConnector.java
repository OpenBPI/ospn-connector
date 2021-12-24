package com.ospn;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ospn.common.ECUtils;
import com.ospn.common.OsnSender;
import com.ospn.common.OsnServer;
import com.ospn.common.OsnUtils;
import com.ospn.data.SyncIDData;
import com.ospn.server.*;
import com.ospn.utils.DBUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.ospn.common.OsnUtils.logError;
import static com.ospn.common.OsnUtils.logInfo;
import static com.ospn.server.OsnSyncNode.*;

public class OsnConnector extends OsnServer {
    public static void main(String[] args) {
        OsnUtils.init("connector");
        OsnConnector.Inst = new OsnConnector();
        OsnConnector.Inst.InitService();
    }

    public static OsnConnector Inst = null;
    public static Properties prop = null;
    public static DBUtils db = null;
    public static String myIP = null;
    public static String imServerIP = null;
    public static String imType = "key";

    //public static int imServicePort = 8100;
    public static int imNotifyPort = 8200;
    //public static int imAdminPort = 8300;
    public static int ospnServicePort = 8400;
    public static int ospnNetworkPort = 8500;
    public static int ospnAdminPort = 8600;
    public static int maxQueryList = 100;

    public static String osnSrvID;
    public static String osnSrvKey;

    public static final Object syncLock = new Object();
    public static final Object queryLock = new Object();
    public static final String osnSyncKey = String.valueOf(System.currentTimeMillis());
    public static final List<String> cfgPeer = new ArrayList<>();
    public static final CopyOnWriteArraySet<String> osnPeer = new CopyOnWriteArraySet<>();
    public static final ConcurrentHashMap<String,PeerInfo> osnPeerInfo = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,QueryData> osnQueryMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,CopyOnWriteArraySet<String>> osnTargetMap = new ConcurrentHashMap<>();   //OsnID -> ip
    public static final ConcurrentHashMap<String,OsnSender> osnSenderMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,OsnSender> osnServiceMap = new ConcurrentHashMap<>();
    public OsnSender imSender = null;
    public JsonSender jsSender = null;

    public static class PeerInfo{
        public String syncKey;
        public String imType;
        public String ip;
    }
    public static class QueryData{
        public String osnID;
        public ConcurrentLinkedQueue<JSONObject> jsonList;
        public long timeStamp;

        QueryData(String osnID){
            this.osnID = osnID;
            jsonList = new ConcurrentLinkedQueue<>();
            timeStamp = System.currentTimeMillis();
        }
        void addJson(JSONObject json){
            if(jsonList.size() < maxQueryList)
                jsonList.offer(json);
        }
    }
    public class JsonSender implements OsnSender.Callback{
        public void onDisconnect(OsnSender sender, String error){
            if(sender.mNetwork) {
                logInfo("network: "+sender.mIp+":"+sender.mPort+"/"+sender.mTarget+", error: "+error);
                osnSenderMap.remove(sender.mIp);
                synchronized (syncLock) {
                    syncLock.notify();
                }
            }
            else{
                logInfo("service: "+sender.mIp+":"+sender.mPort+"/"+sender.mTarget+", error: "+error);
                try {
                    Thread.sleep(3000);
                    OsnSender senderNew = OsnSender.newInstance(sender);
                    if(sender.mTarget == null)
                        imSender = senderNew;
                    else if(osnServiceMap.containsKey(sender.mTarget))
                        osnServiceMap.put(sender.mTarget, senderNew);
                    else
                        logError("unknown target: "+sender.mTarget);
                }
                catch (Exception e){
                    logInfo(e.toString());
                }
            }
        }
        public void onCacheJson(OsnSender sender, JSONObject json){
            logInfo("drop "+(sender.mPort == imNotifyPort?"IM":"OSX")+" json: "+json.getString("command"));
        }
        public List<JSONObject> onReadCache(OsnSender sender, String target, int count){
            return null;
        }
    }

    private void InitService(){
        try {
            db = DBUtils.Inst();
            String[] osnID = ECUtils.createOsnID("service");
            if(osnID == null){
                logError("ECUtils.createOsnID null");
                return;
            }
            osnSrvID = osnID[0];
            osnSrvKey = osnID[1];

            prop = new Properties();
            prop.load(new FileInputStream("ospn.properties"));

            String peers = prop.getProperty("ipPeer");
            cfgPeer.add(peers);
            osnPeer.add(peers);

            myIP = prop.getProperty("ipConnector");
            imServerIP = prop.getProperty("ipIMServer");

            logInfo("myIP: "+myIP);
            logInfo("IMServer: "+imServerIP);
            logInfo("peer: "+peers);

            AddService(ospnServicePort, new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel arg0) {
                    arg0.pipeline().addLast(new MessageDecoder());
                    arg0.pipeline().addLast(new MessageEncoder());
                    arg0.pipeline().addLast(new OsnIMSHandler());
                }
            });
            AddService(ospnNetworkPort, new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel arg0) {
                    arg0.pipeline().addLast(new MessageDecoder());
                    arg0.pipeline().addLast(new MessageEncoder());
                    arg0.pipeline().addLast(new OsnOSXHandler());
                }
            });
            AddService(ospnAdminPort, new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel arg0) {
                    arg0.pipeline().addLast("http-decoder",new HttpRequestDecoder());
                    arg0.pipeline().addLast("aggregator",new HttpObjectAggregator(65536));
                    arg0.pipeline().addLast("http-encoder",new HttpResponseEncoder());
                    arg0.pipeline().addLast("http-chunked",new ChunkedWriteHandler());
                    arg0.pipeline().addLast("handler",new OsnAdminServer(prop));
                }
            });

            OsnSyncNode.initServer();
            OsnFindID.initServer();

            synchronized (syncLock) {
                syncLock.notify();
            }
            jsSender = new JsonSender();
            if(imServerIP != null && !imServerIP.isEmpty())
                imSender = OsnSender.newInstance(imServerIP,imNotifyPort,null,false,2000,jsSender,null);

            String forward = prop.getProperty("serviceForward");
            if(forward != null && !forward.isEmpty()){
                String[] services = forward.split(" ");
                for(String service : services){
                    String[] info = service.split(":");
                    if(info.length == 3){
                        int port = Integer.parseInt(info[2]);
                        OsnSender sender = OsnSender.newInstance(info[1], port, info[0], false,200, jsSender,null);
                        osnServiceMap.put(info[0], sender);
                        logInfo("forward service: "+info[0]+", port: "+port);
                    }
                    else
                        logError("format error: "+service);
                }
            }
            OsnIDSync.initServer();
            OsnBroadcast.initServer();
        }
        catch (Exception e){
            logError(e);
            System.exit(-1);
        }
    }

    public boolean isMyNode(String ip){
        return ip.equalsIgnoreCase("127.0.0.1") || ip.equalsIgnoreCase(myIP);
    }
    public boolean isContain(String ip){
        return isMyNode(ip) || osnPeer.contains(ip);
    }
    public boolean pushJson(String ip, JSONObject json){
        return sendJson3(ip, ospnNetworkPort, json);
    }
    public JSONObject pullJson(String ip, JSONObject json){
        JSONObject data = sendJson2(ip, ospnNetworkPort, json);
        if(data == null){
            logInfo("data == null");
            return null;
        }
        return data;
    }

    public void sendIMJson(JSONObject json){
        if(imSender != null)
            imSender.send((json));
    }
    public void sendSrvJson(JSONObject json){
        if (imSender != null)
            imSender.send(json);
        for (OsnSender sender : osnServiceMap.values())
            sender.send(json);
    }
    public void sendSrvJson(String target, JSONObject json){
        if(target == null){
            if(imSender != null)
                imSender.send((json));
            else
                logError("imSender == null");
        }
        else{
            OsnSender sender = osnServiceMap.get(target);
            if(sender == null)
                logError("unknown target: "+target);
            else
                sender.send(json);
        }
    }
    public void sendOsxJson(String ip, JSONObject json){
        //sendJson(ip, ospnNetworkPort, json);
        if(ip == null)
            return;
        OsnSender sender = osnSenderMap.get(ip);
        if(sender == null) {
            sender = OsnSender.newInstance(ip, ospnNetworkPort, json.getString("to"), true,200, jsSender,null);
            osnSenderMap.put(ip, sender);
        }
        sender.send(json);
    }
    private void sendQuery(String osnID, String ip){
        QueryData queryData = osnQueryMap.remove(osnID);
        if(queryData != null){
            while(!queryData.jsonList.isEmpty()){
                JSONObject pendPackage = queryData.jsonList.poll();
                sendOsxJson(ip, pendPackage);
                OsnUtils.logInfo("forward to OSX(list): " + pendPackage.getString("command"));
            }
        }
    }
    private void delTargetMap(String osnID, String ip){
        CopyOnWriteArraySet<String> ips = osnTargetMap.get(osnID);
        if(ips == null){
            return;
        }
        ips.remove(ip);
    }
    private void addTargetMap(String osnID, String ip){
        CopyOnWriteArraySet<String> ips = osnTargetMap.get(osnID);
        if(ips == null){
            ips = new CopyOnWriteArraySet<>();
            osnTargetMap.put(osnID, ips);
        }
        ips.add(ip);
        osnPeer.add(ip);
    }
    private void setOsnID(String osnID, String ip){
        int type = 0;
        if(osnID.startsWith("OSNG"))
            type = 1;
        else if(osnID.startsWith("OSNS"))
            type = 2;
        db.setSyncIDData(type, ip, osnID, System.currentTimeMillis());
    }

    private class OsnOSXHandler extends SimpleChannelInboundHandler<JSONObject> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, JSONObject json) {
            handleMessage(ctx,json,true);
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            ctx.close();
        }
    }
    private class OsnIMSHandler extends SimpleChannelInboundHandler<JSONObject> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, JSONObject json){
            handleMessage(ctx,json,false);
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            ctx.close();
        }
    }

    private void findOsnID(JSONObject json){
        try {
            String ip = json.getString("ip");
            JSONArray findList = json.getJSONArray("targetList");
            logInfo("findOsnID from node: " + ip + ", targetList: " + findList.toString());
            sendSrvJson(json);
        }
        catch (Exception e){
            logError(e);
        }
    }
    private void syncNode(ChannelHandlerContext ctx, JSONObject json){
        try{
            JSONObject data = getSyncInfo(false);
            ctx.writeAndFlush(data);
            setPeerInfo(json);

            logInfo("sync node: " + json.getString("ip") + ", key: "+json.getString("syncKey"));

            JSONArray nodeList = json.getJSONArray("nodeList");
            for(Object o:nodeList){
                String ip = (String)o;
                if(isContain(ip))
                    continue;
                if(pingNode(ip,ospnNetworkPort)) {
                    logInfo("sync add node: "+ip);
                    osnPeer.add(ip);
                }
            }
        }
        catch (Exception e){
            logError(e);
        }
    }
    private void haveOsnID(JSONObject json, boolean isOsx){
        String ip = json.getString("ip");
        if(ip == null)
            return;
        if(isOsx){
            JSONArray targetList = json.getJSONArray("targetList");
            String timeStamp = json.getString("timeStamp");
            //String querySign = json.getString("querySign");
            //if(!ECUtils.osnVerify(osnSrvID, timeStamp.getBytes(), querySign)){
            //    logError("verify sign error from ip: "+ip);
            //    return;
            //}
            long timestamp = Long.parseLong(timeStamp);
            if(timestamp > System.currentTimeMillis() || timestamp+60*1000 < System.currentTimeMillis()){
                logError("time differ too long from ip: "+ip);
                return;
            }
            if(targetList != null && !targetList.isEmpty()){
                for(Object o:targetList) {
                    JSONObject data = (JSONObject)o;
                    String osnID = data.getString("osnID");
                    //String sign = data.getString("sign");
                    //if(!ECUtils.osnVerify(osnID, timeStamp.getBytes(), sign)){
                    //    logError("osnID: "+osnID+", ip: "+ip);
                    //    continue;
                    //}
                    addTargetMap(osnID, ip);
                    setOsnID(osnID, ip);
                    sendQuery(osnID, ip);
                }
                logInfo("Record Owner haveOsnID: "+targetList.size()+", ip: "+ip);
            }
        }
        else{
            json.put("ip", myIP);
            sendOsxJson(ip, json);
            logInfo("haveOsnID forward to ip: " + ip);
        }
    }
    private void forwardJson(ChannelHandlerContext ctx, JSONObject json, boolean isOsx){
        try{
            String command = json.getString("command");
            String target = json.getString("to");
            if(isOsx){
                if(target == null)
                    return;
                if(osnServiceMap.containsKey(target)){
                    logInfo("forward to SRV: " + command + ", ip: " + getIpAddr(ctx.channel().remoteAddress()));
                    sendSrvJson(target,json);
                }
                else {
                    logInfo("forward to IMS: " + command + ", ip: " + getIpAddr(ctx.channel().remoteAddress()));
                    sendIMJson(json);
                }
            }
            else{
                String ip;
                if (target == null) {
                    logInfo("targetOsnID == null");
                    return;
                }
                CopyOnWriteArraySet<String> ips = osnTargetMap.get(target);
                if(ips != null){
                    Iterator<String> it = ips.iterator();
                    while(it.hasNext()){
                        ip = it.next();
                        if(!osnPeer.contains(ip)){
                            ips.remove(ip);
                        }
                    }
                }
                if (ips != null && !ips.isEmpty()) {
                    for(String ipx : ips){
                        logInfo("forward to OSX: " + command + ", ip: " + ipx);
                        sendOsxJson(ipx, json);
                    }
                } else {
                    logInfo("query osnID: " + command + ", target: " + target);
                    QueryData queryData = osnQueryMap.computeIfAbsent(target,k->new QueryData(target));
                    queryData.addJson(json);
                    synchronized (queryLock){
                        queryLock.notify();
                    }
                    synchronized (syncLock) {
                        syncLock.notify();
                    }
                }
            }
        }
        catch (Exception e){
            logError(e);
        }
    }
    private void syncOsnID(ChannelHandlerContext ctx, JSONObject json){
        String ip = getIpAddr(ctx.channel().remoteAddress());
        String osnID = json.getString("osnID");
        List<SyncIDData> syncIDDataList = db.getSyncIDDatas(osnID, 20);
        json.put("command", "synxOsnID");
        json.put("osnIDList", syncIDDataList);
        sendOsxJson(ip, json);

        logInfo("from: "+ip+", size: "+syncIDDataList.size());
    }
    private void pushOsnID(JSONObject json, boolean isOsx){
        if(isOsx) {
            String tip = json.getString("tip");
            String osnID = json.getString("osnID");
            if (tip == null || osnID == null) {
                logInfo("missing data: "+json);
                return;
            }
            setOsnID(osnID, tip);
            addTargetMap(osnID, tip);
            logInfo("recv osnID: " + json.getString("osnID"));
        } else {
            String osnID = json.getString("osnID");
            json.remove("osnKey");
            json.put("tip", myIP);
            OsnBroadcast.send(json);

            setOsnID(osnID, myIP);
            logInfo("push osnID: "+osnID);
        }
    }
    private void popOsnID(JSONObject json, boolean isOsx){
        if(isOsx){
            String osnID = json.getString("osnID");
            String tip = json.getString("tip");
            delTargetMap(osnID, tip);
        }
    }
    private void broadcast(JSONObject json, boolean isOsx){
        logInfo("isOsx: "+isOsx);
        if(isOsx){
            sendIMJson(json);
        } else {
            OsnBroadcast.send(json);
        }
    }
    private void setImsType(JSONObject json){
        imType = json.getString("type");
        logInfo("type: "+imType);
    }
    private void handleMessage(ChannelHandlerContext ctx, JSONObject json, boolean isOsx){
        try{
            String command = json.getString("command");
            if(command == null)
                return;
            switch(command){
                case "Heart":
                    ctx.writeAndFlush(json);
                    break;
                case "haveOsnID":
                    haveOsnID(json,isOsx);
                    break;
                case "syncNode":
                    syncNode(ctx,json);
                    break;
                case "findOsnID":
                    findOsnID(json);
                    break;
                case "syncOsnID":
                    syncOsnID(ctx, json);
                    break;
                case "synxOsnID":
                    OsnIDSync.synxOsnID(json);
                    break;
                case "pushOsnID":
                    pushOsnID(json, isOsx);
                    break;
                case "popOsnID":
                    popOsnID(json, isOsx);
                    break;
                case "Broadcast":
                    broadcast(json, isOsx);
                    break;
                case "setImsType":
                    setImsType(json);
                    break;
                default:
                    forwardJson(ctx,json,isOsx);
                    break;
            }
        }
        catch (Exception e){
            logError(e);
        }
    }
}
