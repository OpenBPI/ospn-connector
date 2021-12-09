package com.ospn.utils;

import com.alibaba.fastjson.JSONObject;
import com.ospn.common.ECUtils;
import com.ospn.common.OsnUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public class HttpUtils {
    public static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status){
        JSONObject json = new JSONObject();
        json.put("errCode",status.toString());
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,status, Unpooled.copiedBuffer(json.toString(), CharsetUtil.UTF_8));
        response.headers().set("Content-Type", "application/json"); //text/plain;charset=UTF-8
        response.headers().set("Content-Length", response.content().readableBytes());
        ctx.writeAndFlush(response);//.addListener(ChannelFutureListener.CLOSE);
    }
    public static void sendReply(ChannelHandlerContext ctx,JSONObject json){
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK, Unpooled.copiedBuffer(json.toString(), CharsetUtil.UTF_8));
        response.headers().set("Content-Type", "application/json"); //text/plain;charset=UTF-8
        response.headers().set("Content-Length", response.content().readableBytes());
        response.headers().set("Access-Control-Allow-Origin", "*");
        response.headers().set("Access-Control-Allow-Headers", "*");
        //ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        ctx.writeAndFlush(response);
    }
    public static String getKey(String type, String name){
        String prefix;
        if(type != null)
            prefix = type.equalsIgnoreCase("portrait") ? "P":"C";
        else
            prefix = name.startsWith("P") ? "P" : "C";
        name = name + System.currentTimeMillis();
        String key = ECUtils.b58Encode(OsnUtils.sha256(name.getBytes()));
        return prefix+key;
    }
    public static String getDir(String name){
        return name.startsWith("P") ? "portrait/"+name : "cache/"+name;
    }
}
