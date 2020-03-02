import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class osn_connector implements HttpHandler {
    public static void main(String[] args) {
        try {
            osn_connector osnc = new osn_connector();
            String ipIMS = "127.0.0.1";
            if(args.length > 1)
                ipIMS = args[1];
            osnc.mIMServer = "http://"+ipIMS+":8100/ims";

            HttpServer httpServer = HttpServer.create(new InetSocketAddress(8400), 0);
            httpServer.createContext("/osnc", osnc);
            httpServer.start();
            osnc.logInfo("StartOSNCServer port: 8400");

            httpServer = HttpServer.create(new InetSocketAddress(8500), 0);
            httpServer.createContext("/osnx", osnc);
            httpServer.start();
            osnc.logInfo("StartOSNXServer port: 8500");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private String mIMServer = null;

    private void logInfo(String info){
        SimpleDateFormat formatter= new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss] ");
        Date date = new Date(System.currentTimeMillis());
        String time = formatter.format(date);
        System.out.print(time);
        System.out.println(info);
    }
    private JSONObject error(String errCode, String errMsg){
        JSONObject json = new JSONObject();
        json.put("errCode", errCode);
        json.put("errMsg", errMsg);
        return json;
    }
    private JSONObject errorOK(){
        JSONObject json = new JSONObject();
        json.put("errCode", "0");
        return json;
    }
    private JSONObject errExcept(String msg){
        return error("-1", msg);
    }
    private JSONObject errNeedPost(){
        return error("1001", "only support POST");
    }
    private JSONObject errNeedBody(){
        return error("1002", "need body");
    }
    private JSONObject errUnknowCommand(){
        return error("1003", "no support command");
    }
    private JSONObject errResponseCode(int errCode){
        return error(String.valueOf(errCode), "Response error");
    }
    private JSONObject errFormat(){
        return error("1004", "error format");
    }
    private JSONObject errUser(){
        return error("1005", "can't find user");
    }
    private JSONObject errNoData(){return error("1006", "can't find data");}
    private JSONObject errAuth(){return error("1007", "user auth failed");}

    private JSONObject getBody(InputStream inputStream){
        JSONObject json = null;
        try {
            StringBuilder sb = new StringBuilder();
            byte[] bb = new byte[1024];
            int length = -1;
            while ((length = inputStream.read(bb)) != -1) {
                sb.append(new String(bb, 0, length));
            }
            json = JSONObject.parseObject(sb.toString());
        }
        catch (Exception e){
            json = errExcept(e.getMessage());
        }
        return json;
    }
    private JSONObject doPost(String urlString, JSONObject json){
        try{
            URL url = new URL(urlString);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("accept", "*/*");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setDoInput(true);
            //httpURLConnection.setConnectTimeout(5000);
            //httpURLConnection.setReadTimeout(5000);
            if(json != null)
                httpURLConnection.getOutputStream().write(json.toString().getBytes());
            else
                httpURLConnection.connect();
            if (httpURLConnection.getResponseCode() != 200){
                json = errResponseCode(httpURLConnection.getResponseCode());
            }
            else {
                InputStream inputStream = httpURLConnection.getInputStream();
                json = getBody(inputStream);
            }
        }
        catch (Exception e){
            e.printStackTrace();
            json = errExcept(e.getMessage());
        }
        return json;
    }
    private boolean isOSNX(HttpExchange exchange){
        String path = exchange.getRequestURI().getPath();
        return path.equalsIgnoreCase("/osnx");
    }
    private String getNodeIP(String node){
        String urlString = "http://127.0.0.1:5001/api/v0/swarm/peers?&encoding=json&stream-channels=true";
        JSONObject json = doPost(urlString, null);
        JSONArray array = json.getJSONArray("Peers");
        for(int i = 0; i < array.size(); ++i){
            if(node.equalsIgnoreCase(array.getJSONObject(i).getString("Peer"))){
                String ips = array.getJSONObject(i).getString("Addr");
                String[] data = ips.split("/");
                return data[2];
            }
        }
        return "";
    }
    private void respone(HttpExchange exchange, JSONObject json){
        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(json.toString().getBytes());
            outputStream.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private JSONObject forward(String ip, JSONObject json){
        String url = "http://" + ip + ":8500/osnx";
        return doPost(url, json);
    }
    private void GetHash(HttpExchange exchange, JSONObject json){
        String osnID = json.getString("user");
        if(osnID == null){
            respone(exchange, errFormat());
            return;
        }
        String idHash = IDUtil.GetHash(osnID);

        json.clear();
        json.put("errCode", "0");
        json.put("hash", idHash);
        respone(exchange, json);
    }
    private void Notify(HttpExchange exchange, JSONObject json){
        String node = json.getString("node");
        String key = json.getString("key");
        String ip = getNodeIP(node);
        logInfo("[Notify] ip: " + ip + ", hash: " + key + ", node: " + node);
        json.clear();
        json.put("command", "finduser");
        json.put("hash", key);
        json.put("ip", ip);
        json = doPost(mIMServer, json);
        respone(exchange, json);
    }
    private void GetMessage(HttpExchange exchange, JSONObject json){
        if(isOSNX(exchange)){
            logInfo("[GetMessage <-] ");
            json = doPost(mIMServer, json);
            respone(exchange, json);
        } else{
            String ip = json.getString("ip");
            String from = json.getString("from");
            logInfo("[GetMessage ->] ip: " + ip + ", from: " + from);
            if(ip == null){
                respone(exchange, errFormat());
                return;
            }
            json = forward(ip, json);
            respone(exchange, json);
        }
    }
    private void GetMsgList(HttpExchange exchange, JSONObject json){
        if(isOSNX(exchange)) {
            logInfo("[GetMsgList <-]");
            json = doPost(mIMServer, json);
            respone(exchange, json);
        } else {
            String ip = json.getString("ip");
            String hash = json.getString("hash");
            if (ip == null) {
                respone(exchange, errFormat());
                return;
            }
            logInfo("[GetMsgList ->] ip: " + ip + ", hash: " + hash);
            json = forward(ip, json);
            respone(exchange, json);
        }
    }
    private void SendMessage(HttpExchange exchange, JSONObject json){
        String user = json.getString("to");
        if(user == null) {
            respone(exchange, errUser());
            return;
        }
        String hash = IDUtil.GetHash(user);
        logInfo("[SendMessage] to: " + user + ", hash: " + hash);
        String urlString = "http://127.0.0.1:5001/api/v0/dht/findprovs?arg=" + hash + "&encoding=json&num-providers=1&stream-channels=true";
        doPost(urlString, null);

        json.clear();
        json.put("errCode", "0");
        respone(exchange, json);
    }
    private void SetComplete(HttpExchange exchange, JSONObject json){
        if(isOSNX(exchange)){
            logInfo("[SetComplete <-]");
            json = doPost(mIMServer, json);
            respone(exchange, json);
        } else {
            String ip = json.getString("ip");
            logInfo("[SetComplete ->] ip: " + ip);
            if(ip == null){
                respone(exchange, errFormat());
                return;
            }
            json = forward(ip, json);
            respone(exchange, json);
        }
    }
    public void handle(HttpExchange exchange) {
        try {
            String requestMethod = exchange.getRequestMethod();
            if (requestMethod.equalsIgnoreCase("POST")) {
                InputStream inputStream = exchange.getRequestBody();
                JSONObject json = getBody(inputStream);
                if(json.containsKey("errCode")){
                    respone(exchange, json);
                    return;
                }
                String command = json.getString("command");
                if ("notify".equalsIgnoreCase(command)) {
                    Notify(exchange, json);
                } else if ("gethash".equalsIgnoreCase(command)) {
                    GetHash(exchange, json);
                } else if ("getmsg".equalsIgnoreCase(command)) {
                    GetMessage(exchange, json);
                } else if ("getmsglist".equalsIgnoreCase(command)) {
                    GetMsgList(exchange, json);
                } else if ("message".equalsIgnoreCase(command)) {
                    SendMessage(exchange, json);
                } else if ("complete".equalsIgnoreCase(command)) {
                    SetComplete(exchange, json);
                } else {
                    respone(exchange, errUnknowCommand());
                }
            }
            else
                respone(exchange, errNeedPost());
        }
        catch (Exception e){
            logInfo(e.getMessage());
        }
    }
}
