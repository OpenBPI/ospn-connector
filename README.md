# ospn-connector

ospn-connector ��crossim�Ķ������������

## ����
ospn-connector�ı�������openssl����Ҫ����openssl��so�⡣
 
## ����
**�������ݿ�**
��c3p0-config.xml�ļ�

'''xml
<?xml version ="1.0" encoding="UTF-8"?>
<c3p0-config>
    <default-config>
        <property name="driverClass">com.mysql.cj.jdbc.Driver</property>
        <property name="jdbcUrl">jdbc:mysql://[����myaql���ݿ��ַ]/[�������ݿ���]?useSSL=true&amp;verifyServerCertificate=false&amp;serverTimezone=GMT%2B8&amp;allowPublicKeyRetrieval=true&amp;useUnicode=true&amp;characterEncoding=UTF-8&amp;autoReconnect=true</property>
        <property name="user">[�������ݿ��¼�û���]</property>
        <property name="password">[�������ݿ�����]</property>
    </default-config>
</c3p0-config>

'''
����myaql���ݿ��ַ

�������ݿ���

�������ݿ��¼�û���

�������ݿ�����

** ���÷���IP��ַ **
��ospn.properties�ļ�

'''
ipConnector= ����connector IP��ַ
ipIMServer= ����ims IP��ַ
ipPeer= �����ڽ��ڵ�IP��ַ
'''

## ����
ospn-connector�Ĳ�����Ҫ��ims��ͬ���������ospn-ims-share��


## ����˵��
2021-12-23 �����������ڵ�������Ϣ��֧�ֹ���IM�ڵ㡣