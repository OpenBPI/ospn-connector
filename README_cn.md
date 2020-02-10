OSN-connector=============
=`OSN-connector` 是一个跨界通讯的服务，企业或者个人的IM服务通过与OSN-connector交互可以让不同应用（APP）上的用户进行跨界交流。---------------------------------------##安装###一、编译IPFSOSN-connector目前采用了IPFS的p2p模块，并进行了简单的修改。
>IPFS下载地址
```https://github.com/ipfs/go-ipfs```
下载完成以后需要修改两个地方
1、把查询回应去掉。 
2、把查询信息post给connector
打开go-ipfs-master/cmd/ipfs/main.go文件点击编译。
注：如不想自行修改也可以直接下载我们已经修改好的代码进行编译。

二、编译OSN-connector
TODO

三、部署
1.修改ipfs可执行标志(如何修改？)
2.初始化ipfs 
```./ipfs init```
3.启动ipfs后台 
```nohup ./ipfs daemon > ipfs.log &```
4.查询ipfs节点ID 
```./ipfs id```
5.连接到其他节点 
```./ipfs swarm connect [id]```
6.启动osn服务 
```nohup java -jar osn-connector.jar > connector.log &```

配置
OSN-connector需要配置与其交互数据的IM服务
```nohup java -jar osn_connector.jar [ip] > connector.log &```
ip为IM服务的IP,端口目前固定为8100

文档
OSN-connector接口文档

