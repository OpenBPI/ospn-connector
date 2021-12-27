# ospn-connector

ospn-connector 是crossim的对外链接组件。

## 编译
ospn-connector的编译依赖openssl，需要编译openssl的so库。
 
## 配置
**配置数据库**
打开c3p0-config.xml文件

'''xml
<?xml version ="1.0" encoding="UTF-8"?>
<c3p0-config>
    <default-config>
        <property name="driverClass">com.mysql.cj.jdbc.Driver</property>
        <property name="jdbcUrl">jdbc:mysql://[配置myaql数据库地址]/[配置数据库名]?useSSL=true&amp;verifyServerCertificate=false&amp;serverTimezone=GMT%2B8&amp;allowPublicKeyRetrieval=true&amp;useUnicode=true&amp;characterEncoding=UTF-8&amp;autoReconnect=true</property>
        <property name="user">[配置数据库登录用户名]</property>
        <property name="password">[配置数据库密码]</property>
    </default-config>
</c3p0-config>

'''
配置myaql数据库地址

配置数据库名

配置数据库登录用户名

配置数据库密码

** 配置服务IP地址 **
打开ospn.properties文件

'''
ipConnector= 配置connector IP地址
ipIMServer= 配置ims IP地址
ipPeer= 配置邻近节点IP地址
'''

## 部署
ospn-connector的部署需要与ims共同部署，请参阅ospn-ims-share。


## 更新说明
2021-12-23 更新了向多个节点推送消息，支持共享IM节点。