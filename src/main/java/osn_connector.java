import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sun.rmi.runtime.Log;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class osn_connector {
    public static void main(String[] args) {
        try {
            osn_connector connector = new osn_connector();
            connector.mIMServerIP = "127.0.0.1";
            connector.mLogFileName = "connector.log";
            if(args.length > 1)
                connector.mIMServerIP = args[0];
            connector.StartConnector();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private String mIMServerIP = null;
    private String mLogFileName = null;
    private int mIMServerPort = 8100;
    private int mOsnCPort = 8400;
    private int mOsnXPort = 8500;
    private ExecutorService mExecutorService  = Executors.newFixedThreadPool(2);

    private JSONObject error(String errCode){
        JSONObject json = new JSONObject();
        json.put("errCode", errCode);
        return json;
    }
    private JSONObject errExcept(String msg){return error(msg);}
    private JSONObject errResponse(int errCode){return error("Response error" + String.valueOf(errCode));}

    private String getBody(InputStream inputStream){
        try {
            StringBuilder sb = new StringBuilder();
            byte[] bb = new byte[1024];
            int length = -1;
            while ((length = inputStream.read(bb)) != -1) {
                sb.append(new String(bb, 0, length));
            }
            return sb.toString();
        }
        catch (Exception e){
            OsnUtils.logInfo(e.toString());
        }
        return null;
    }
    private JSONObject doPost(String urlString, Boolean needResp){
        JSONObject json = null;
        try{
            URL url = new URL(urlString);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("accept", "*/*");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setReadTimeout(5000);
            httpURLConnection.connect();
            if (httpURLConnection.getResponseCode() != 200){
                OsnUtils.logInfo("respone error: " + httpURLConnection.getResponseCode());
            }
            else {
                InputStream inputStream = httpURLConnection.getInputStream();
                String text = getBody(inputStream);
                json = JSONObject.parseObject(text);
            }
        }
        catch (Exception e){
            if(needResp)
                OsnUtils.logInfo(e.toString());
        }
        return json;
    }

    private void sendJson(String ip, int port, JSONObject json){
        try {
            byte[] jsonData = json.toString().getBytes();
            byte[] data = new byte[jsonData.length+4];
            data[0] = (byte)((jsonData.length>>24)&0xff);
            data[1] = (byte)((jsonData.length>>16)&0xff);
            data[2] = (byte)((jsonData.length>>8)&0xff);
            data[3] = (byte)(jsonData.length&0xff);
            System.arraycopy(jsonData, 0, data, 4, jsonData.length);
            Socket socket = new Socket(ip, port);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(data);
            socket.close();
        }
        catch (Exception e){
            OsnUtils.logInfo(e.toString());
        }
    }
    private void sendIMJson(JSONObject json){
        sendJson(mIMServerIP, mIMServerPort, json);
    }
    private void sendOsnJson(String ip, JSONObject json){
        sendJson(ip, mOsnXPort, json);
    }
    private void handleMessage(SocketChannel socketChannel, String data){
        try {
            //OsnUtils.logInfo("data = " + data);
            JSONObject json = JSONObject.parseObject(data);
            Socket socket = socketChannel.socket();
            if (socket.getLocalPort() == mOsnCPort) { //come from ims
                String ip = json.getString("ip");
                if (ip == null) {
                    String user = json.getString("to");
                    if (user == null) {
                        OsnUtils.logInfo("notify user miss");
                    } else {
                        String hash = OsnUtils.getHash(user);
                        OsnUtils.logInfo("notify command: " + json.getString("command") + " to: " + user);
                        String urlString = "http://127.0.0.1:5001/api/v0/dht/findprovs?arg=" + hash + "&encoding=json&num-providers=1&stream-channels=true";
                        doPost(urlString, false);
                    }
                }
                else {
                    json.remove("ip");
                    if(json.containsKey("errCode"))
                        OsnUtils.logInfo("respond command: " + json.getString("command") + ", to: " + ip);
                    else
                        OsnUtils.logInfo("request command: " + json.getString("command") + ", to: " + ip);
                    sendOsnJson(ip, json);
                }
            }
            else{
                if(json.getString("command").equalsIgnoreCase("finduser")) {
                    String node = json.getString("node");
                    String urlString = "http://127.0.0.1:5001/api/v0/swarm/peers?&encoding=json&stream-channels=true";
                    JSONObject peerJson = doPost(urlString, true);
                    JSONArray array = peerJson.getJSONArray("Peers");
                    String ip = "";
                    for(int i = 0; i < array.size(); ++i){
                        if(node.equalsIgnoreCase(array.getJSONObject(i).getString("Peer"))){
                            String ips = array.getJSONObject(i).getString("Addr");
                            String[] ipData = ips.split("/");
                            ip = ipData[2];
                            break;
                        }
                    }
                    OsnUtils.logInfo("finduser from ip: " + ip + ", hash: " + json.getString("hash") + ", node: " + node);
                    json.put("ip", ip);
                    sendIMJson(json);
                }
                else {
                    String ip = socketChannel.getRemoteAddress().toString();
                    if(ip.startsWith("/"))
                        ip = ip.substring(1);
                    String[] ips = ip.split(":");
                    ip = ips[0];
                    json.put("ip", ip);
                    if(json.containsKey("errCode"))
                        OsnUtils.logInfo("respond command: " + json.getString("command") + ", from: " + ip);
                    else
                        OsnUtils.logInfo("request command: " + json.getString("command") + ", from: " + ip);
                    sendIMJson(json);
                }
            }
        }
        catch (Exception e){
            OsnUtils.logInfo(e.toString());
        }
    }
    private void handlePackage(SelectionKey key){
        try {
            SocketChannel socketChannel = (SocketChannel)key.channel();
            byte[] recv = new byte[4096];
            ByteBuffer buffer = ByteBuffer.wrap(recv);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int readLength = 0;
            while(readLength >= 0) {
                while(true){
                    buffer.clear();
                    readLength = socketChannel.read(buffer);
                    if(readLength <= 0)
                        break;
                    baos.write(recv, 0, readLength);
                }
                if(baos.size() == 0)
                    break;
                byte[] data = baos.toByteArray();
                int length = ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | (data[3] & 0xff);
                if (length + 4 > data.length) {
                    OsnUtils.logInfo("package length error: " + String.valueOf(length) + ", data length: " + String.valueOf(data.length));
                    break;
                }
                baos.reset();
                baos.write(data, 4, length);
                handleMessage(socketChannel, baos.toString());
                baos.reset();
                baos.write(data, length+4, data.length-length-4);
            }
            if(readLength < 0){
                //OsnUtils.logInfo("client disconnect: " + socketChannel.toString());
                key.cancel();
                socketChannel.close();
            }
        }
        catch (IOException e) {
            OsnUtils.logInfo(e.toString());
        }
    }
    private void StartConnector(){
        try {
            OsnUtils.mLogFileName = mLogFileName;
            Selector selector = Selector.open();
            ServerSocketChannel serverChannelhannel0 = ServerSocketChannel.open();
            ServerSocketChannel serverChannelhannel1 = ServerSocketChannel.open();
            serverChannelhannel0.socket().bind(new InetSocketAddress(mOsnCPort));
            serverChannelhannel1.socket().bind(new InetSocketAddress(mOsnXPort));
            serverChannelhannel0.configureBlocking(false);
            serverChannelhannel1.configureBlocking(false);
            serverChannelhannel0.register(selector, SelectionKey.OP_ACCEPT);
            serverChannelhannel1.register(selector, SelectionKey.OP_ACCEPT);
            OsnUtils.logInfo("Start Connector in 8400/8500");
            while (true){
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()){
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()){
                        ServerSocketChannel socketChannel = (ServerSocketChannel) key.channel();
                        SocketChannel channel = socketChannel.accept();
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ);
                        //OsnUtils.logInfo("client connect: " + channel.toString());
                    }
                    if (key.isReadable()){
                        key.interestOps(key.interestOps()&~SelectionKey.OP_READ);
                        mExecutorService.submit(() -> {
                            handlePackage(key);
                            key.interestOps(key.interestOps()|SelectionKey.OP_READ);
                        });
                    }
                    if (key.isWritable()) {
                    }
                    iterator.remove();
                }
            }
        }
        catch (Exception e){
            OsnUtils.logInfo(e.toString());
        }
    }
}
