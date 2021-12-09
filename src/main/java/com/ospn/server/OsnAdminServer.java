package com.ospn.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import com.ospn.common.OsnUtils;
import com.ospn.data.ErrorData;
import com.ospn.utils.HttpUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.util.Properties;

import static com.ospn.Constant.*;
import static com.ospn.OsnConnector.*;
import static com.ospn.common.OsnUtils.*;
import static io.netty.util.CharsetUtil.UTF_8;

public class OsnAdminServer extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final String adminKey;
    private String versionInfo = "v1.2 2021-3-27, base line, long link, findOsnID verify, LTP forward, osnid sync";

    public OsnAdminServer(Properties prop){
        adminKey = prop.getProperty("adminKey");
    }

    private JSONObject replay(ErrorData error, String data){
        JSONObject json = new JSONObject();
        json.put("errCode", error==null?"0:success":error.toString());
        json.put("data",data);
        if(error != null)
            logInfo(error.toString());
        return json;
    }
    private JSONObject getData(JSONObject json){
        String data = json.getString("data");
        return JSON.parseObject(OsnUtils.aesDecrypt(data, adminKey));
    }
    private void setData(JSONObject json){
        String data = json.getString("data");
        if(data != null)
            json.put("data", OsnUtils.aesEncrypt(data, adminKey));
    }
    public JSONObject nodeList(JSONObject json){
        try {
            json.clear();
            json.put("cfgPeer",cfgPeer);
            json.put("osnPeer",osnPeer);
            return replay(null,json.toString());
        }
        catch (Exception e){
            logError(e);
            return replay(new ErrorData("-1",e.toString()),null);
        }
    }
    public JSONObject targetMap(JSONObject json){
        try {
            json.clear();
            json.put("targetMap",osnTargetMap);
            return replay(null,json.toString());
        }
        catch (Exception e){
            logError(e);
            return replay(new ErrorData("-1",e.toString()),null);
        }
    }
    public JSONObject queryMap(JSONObject json){
        try {
            json.clear();
            json.put("queryMap",osnQueryMap);
            return replay(null,json.toString());
        }
        catch (Exception e){
            logError(e);
            return replay(new ErrorData("-1",e.toString()),null);
        }
    }
    public JSONObject senderMap(JSONObject json){
        try {
            json.clear();
            json.put("senderMap",osnSenderMap);
            return replay(null,json.toString());
        }
        catch (Exception e){
            logError(e);
            return replay(new ErrorData("-1",e.toString()),null);
        }
    }
    public JSONObject handleAdmin(JSONObject json){
        JSONObject result;
        try{
            result = getData(json);
            switch(json.getString("command")){
                case "nodeList":
                    result = nodeList(result);
                    break;
                case "targetMap":
                    result = targetMap(result);
                    break;
                case "queryMap":
                    result = queryMap(result);
                    break;
                case "senderMap":
                    result = senderMap(result);
                    break;
                default:
                    result = replay(E_errorCmd,null);
                    break;
            }
            setData(result);
        }
        catch (Exception e){
            result = replay(new ErrorData("-1",e.toString()),null);
            logError(e);
        }
        return result;
    }
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        try {
            JSONObject json;
            if (fullHttpRequest.method() == HttpMethod.POST) {
                String content = fullHttpRequest.content().toString(UTF_8);
                json = JSON.parseObject(content);
                json = handleAdmin(json);
            }
            else if(fullHttpRequest.method() == HttpMethod.GET){
                json = new JSONObject();
                if(fullHttpRequest.uri().equalsIgnoreCase("/version")){
                    json.put("version", versionInfo);
                }
                else if(fullHttpRequest.uri().equalsIgnoreCase("/osnid")){
                    int userCount = db.getSyncIDCount(0);
                    int groupCount = db.getSyncIDCount(1);
                    int serviceCount = db.getSyncIDCount(2);
                    json.put("userCount", userCount);
                    json.put("groupCount", groupCount);
                    json.put("serviceCount", serviceCount);
                }
                else
                    json.put("errCode", "unsupport uri");
            }
            else{
                json = new JSONObject();
                json.put("errCode", "unsupport method");
            }
            HttpUtils.sendReply(ctx,json);
        }
        catch (Exception e){
            logError(e);
        }
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
