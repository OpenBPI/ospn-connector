package com.ospn.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ospn.common.OsnUtils.logError;
import static com.ospn.common.OsnUtils.logInfo;

public class OsnServer {
    EventLoopGroup bossGruop = new NioEventLoopGroup();
    EventLoopGroup workGroup = new NioEventLoopGroup();
    //ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public static class MessageDecoder extends LengthFieldBasedFrameDecoder {
        public MessageDecoder(){
            super(1024*64, 0, 4);
        }
        @Override
        protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            try {
                in = (ByteBuf) super.decode(ctx, in);
                if (in == null)
                    return null;
                byte[] head = new byte[4];
                in.readBytes(head);
                int length = ((head[0]&0xff)<<24) | ((head[1]&0xff)<<16) | ((head[2]&0xff)<<8) | head[3]&0xff;
                if(length != in.readableBytes())
                    logError("length mismatch: "+length+":"+in.readableBytes());
                byte[] data = new byte[in.readableBytes()];
                in.readBytes(data);

                try {
                    return JSONObject.parseObject(new String(data));
                } catch (Exception e) {
                    logError(e.toString());
                }
            }
            catch (Exception e){
                logError(e.toString());
            }
            return null;
        }
    }
    public static class MessageEncoder extends MessageToByteEncoder<JSONObject> {
        @Override
        protected void encode(ChannelHandlerContext ctx, JSONObject json, ByteBuf out) throws Exception {
            try {
                byte[] data = json.toString().getBytes();
                out.writeInt(data.length);
                out.writeBytes(data);
            }
            catch (Exception e){
                logError(e.toString());
            }
        }
    }
    public String getIpAddr(SocketAddress address){
        String ip = address.toString();
        return ip.substring(1, ip.indexOf(':'));
    }
    public String getPort(SocketAddress address){
        String ip = address.toString();
        return ip.substring(ip.indexOf(':')+1);
    }
    public boolean pingNode(String ip, int port){
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip,port),1000);
            socket.close();
            return true;
        }
        catch (Exception e){
            logInfo(e + ", ip: "+ip);
        }
        finally {
            try {
                if (socket != null)
                    socket.close();
            }
            catch (Exception e){
                logInfo(e + ", ip: "+ip);
            }
        }
        return false;
    }
    public void sendJson(String ip, int port, JSONObject json){
        //executorService.execute(()->{
            Socket socket = null;
            try {
                byte[] jsonData = json.toString().getBytes();
                byte[] jsonHead = new byte[4];
                jsonHead[0] = (byte)((jsonData.length>>24)&0xff);
                jsonHead[1] = (byte)((jsonData.length>>16)&0xff);
                jsonHead[2] = (byte)((jsonData.length>>8)&0xff);
                jsonHead[3] = (byte)(jsonData.length&0xff);
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip,port),5000);
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(jsonHead);
                outputStream.write(jsonData);
                outputStream.flush();
            }
            catch (Exception e){
                logInfo(e + ", ip: "+ip);
            }
            finally {
                try {
                    if (socket != null)
                        socket.close();
                }
                catch (Exception e){
                    logInfo(e + ", ip: "+ip);
                }
            }
        //});
    }
    public JSONObject sendJson2(String ip, int port, JSONObject json){
        JSONObject data = null;
        Socket socket = null;
        try {
            byte[] jsonData = json.toString().getBytes();
            byte[] jsonHead = new byte[4];
            jsonHead[0] = (byte)((jsonData.length>>24)&0xff);
            jsonHead[1] = (byte)((jsonData.length>>16)&0xff);
            jsonHead[2] = (byte)((jsonData.length>>8)&0xff);
            jsonHead[3] = (byte)(jsonData.length&0xff);
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip,port),5000);
            socket.setSoTimeout(5000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(jsonHead);
            outputStream.write(jsonData);
            outputStream.flush();

            InputStream inputStream = socket.getInputStream();
            Arrays.fill(jsonHead,(byte)0);
            if(inputStream.read(jsonHead) == 4){
                int length = ((jsonHead[0]&0xff)<<24) | ((jsonHead[1]&0xff)<<16) | ((jsonHead[2]&0xff)<<8) | jsonHead[3]&0xff;
                byte[] rData = new byte[length];
                int readed = 0;
                while(readed < length){
                    int len = inputStream.read(rData, readed, length - readed);
                    if(len <= 0)
                        break;
                    readed += len;
                }
                data = JSON.parseObject(new String(rData));
            }
        }
        catch (Exception e){
            logInfo(e + ", ip: "+ip);
        }
        finally {
            try{
                if(socket != null)
                    socket.close();
            }
            catch (Exception e){
                logInfo(e + ", ip: "+ip);
            }
        }
        return data;
    }
    public boolean sendJson3(String ip, int port, JSONObject json){
        boolean sFlag = true;
        Socket socket = null;
        try {
            byte[] jsonData = json.toString().getBytes();
            byte[] jsonHead = new byte[4];
            jsonHead[0] = (byte)((jsonData.length>>24)&0xff);
            jsonHead[1] = (byte)((jsonData.length>>16)&0xff);
            jsonHead[2] = (byte)((jsonData.length>>8)&0xff);
            jsonHead[3] = (byte)(jsonData.length&0xff);
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip,port),5000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(jsonHead);
            outputStream.write(jsonData);
            outputStream.flush();
        }
        catch (Exception e){
            sFlag = false;
            logInfo(e + ", ip: "+ip);
        }
        finally {
            try{
                if(socket != null)
                    socket.close();
            }
            catch (Exception e){
                logInfo(e + ", ip: "+ip);
            }
        }
        return sFlag;
    }
    public void AddService(int port, ChannelHandler childHandler){
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGruop, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_SNDBUF, 1024*64)
                    .childOption(ChannelOption.SO_RCVBUF, 1024*64)
                    .childHandler(childHandler);
            b.bind(port).sync();
            logInfo("Start listening in port: " + port);
        }
        catch (Exception e){
            logError(e);
        }
    }
}
