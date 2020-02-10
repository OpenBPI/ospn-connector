# OSN-CONNECTOR 接口文档
IM服务（以下简称IMS）的作用是与OSN-connector(以下简称OSNC)进行数据交互。
IMS与OSNC之间通过http进行通信，通信格式为json格式。

1. 发送消息  
当IMS上有消息积累时，会通过OSNC通知对方取走数据。
该命令由IMS发送给OSNC，单向发送。
```
{
command:message
to:[OSNID]
}
```

2. 查找用户  
当OSNC接收到其他OSNC的查找用户消息时会通知OSNC。
该命令由OSNC发送给IMS，单向发送。
```
{
command:finduser
hash:[OSNID的hash]
ip:[target ip]
}
```

3. 获取消息来源列表  
当IMS接收到finduser，并判断finduser为自己的用户时，发送消息获取消息来源列表
该命令由IMS发送给OSNC。
```
{
command:getmsglist
hash:[OSNID的hash]
ip:[来自finduser的ip]
}
```
IMS回复
```
{
to:OSNID
from:[OSNID1,OSNID2,OSNID3...]
}
```

4. 获取用户消息
该命令由IMS发送给OSNC。
```
{
command:getmsg
from:[OSNID来源方]
to:[OSNID接收方]
ip:[target ip]
}
```
IMS接收到来自OSNC的getmsg消息以后的回复
```{[msg1,msg2,msg3...]}```

5. OSNID转hash
该命令由IMS发送给OSNC
```
{
command:gethash
user:OSNID
}
```
回复：
```
{
errCode:0
hash:ipsf user hash
}
```