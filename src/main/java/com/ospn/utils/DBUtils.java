package com.ospn.utils;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.ospn.data.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.ospn.common.OsnUtils.logError;

public class DBUtils {
    private static ComboPooledDataSource comboPooledDataSource = null;
    private static DBUtils inst = null;

    public static DBUtils Inst(){
        if(inst == null) {
            inst = new DBUtils();
            inst.initDB();
        }
        return inst;
    }
    public void initDB() {
        comboPooledDataSource = new ComboPooledDataSource();
        try {
            String url01 = comboPooledDataSource.getJdbcUrl().substring(0, comboPooledDataSource.getJdbcUrl().indexOf("?"));
            String datasourceName = url01.substring(url01.lastIndexOf("/") + 1);

            String jdbc = comboPooledDataSource.getJdbcUrl().replace(datasourceName, "");
            Connection connection = DriverManager.getConnection(jdbc, comboPooledDataSource.getUser(), comboPooledDataSource.getPassword());
            Statement statement = connection.createStatement();

            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + datasourceName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");

            statement.close();
            connection.close();
            createTable();

        } catch (Exception e) {
            logError(e);
            System.exit(-1);
        }
    }

    private void createTable() {
        try {
            String[] sqls = {
                    "CREATE TABLE IF NOT EXISTS t_osnid " +
                            "(id INTEGER NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
                            " type tinyint NOT NULL, " +
                            " osnID char(128) NOT NULL UNIQUE, " +
                            " timeStamp bigint NOT NULL, " +
                            " tip char(32) NOT NULL)",

                    "CREATE TABLE IF NOT EXISTS t_syncinfo " +
                            "(id INTEGER NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
                            " ip char(32) NOT NULL UNIQUE, " +
                            " osnID char(128) NOT NULL)",
            };
            Connection connection = comboPooledDataSource.getConnection();
            Statement stmt = connection.createStatement();
            for (String sql : sqls)
                stmt.executeUpdate(sql);
            stmt.close();
            connection.close();
        } catch (Exception e) {
            logError(e);
            System.exit(-1);
        }
    }

    private void closeDB(Connection connection, PreparedStatement statement, ResultSet rs) {
        try {
            if (rs != null)
                rs.close();
        } catch (Exception e) {
            logError(e);
        }
        try {
            if (statement != null)
                statement.close();
        } catch (Exception e) {
            logError(e);
        }
        try {
            if (connection != null)
                connection.close();
        } catch (Exception e) {
            logError(e);
        }
    }

    private SyncIDData toSyncIDData(ResultSet rs) {
        try {
            SyncIDData syncIDData = new SyncIDData();
            syncIDData.type = rs.getInt("type");
            syncIDData.osnID = rs.getString("osnID");
            syncIDData.timeStamp = rs.getLong("timeStamp");
            syncIDData.tip = rs.getString("tip");
            return syncIDData;
        } catch (Exception e) {
            logError(e);
        }
        return null;
    }

    public String getPeerSyncOsnID(String ip){
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String sql = "select * from t_syncinfo where ip=?";
            connection = comboPooledDataSource.getConnection();
            statement = connection.prepareStatement(sql);
            statement.setString(1, ip);
            rs = statement.executeQuery();
            if (rs.next())
                return rs.getString("osnID");
        } catch (Exception e) {
            logError(e);
        } finally {
            closeDB(connection, statement, rs);
        }
        return null;
    }
    public void setPeerSyncOsnID(String ip, String osnID){
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String sql = "update t_syncinfo set osnID=? where ip=?";
            connection = comboPooledDataSource.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(sql);
            statement.setString(1, osnID);
            statement.setString(2, ip);
            statement.executeUpdate();
        } catch (Exception e) {
            logError(e);
        } finally {
            closeDB(connection, statement, rs);
        }
    }
    public int getSyncIDCount(int type){
        int count = 0;
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String sql = "select count(*) from t_osnid where type=?";
            connection = comboPooledDataSource.getConnection();
            statement = connection.prepareStatement(sql);
            statement.setInt(1, type);
            rs = statement.executeQuery();
            if(rs.next())
                count = rs.getInt(1);
        } catch (Exception e) {
            logError(e);
        } finally {
            closeDB(connection, statement, rs);
        }
        return count;
    }
    public List<SyncIDData> getSyncIDDatas(String osnID, int limit) {
        List<SyncIDData> syncIDDatas = new ArrayList<>();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String sql = osnID == null
                    ? "select * from t_osnid limit ?"
                    : "select * from t_osnid where id>(select id from t_osnid where osnID=?) limit ?";
            connection = comboPooledDataSource.getConnection();
            statement = connection.prepareStatement(sql);
            if(osnID == null){
                statement.setInt(1, limit);
            }
            else{
                statement.setString(1, osnID);
                statement.setInt(2, limit);
            }
            rs = statement.executeQuery();
            while (rs.next()) {
                SyncIDData syncIDData = toSyncIDData(rs);
                syncIDDatas.add(syncIDData);
            }
        } catch (Exception e) {
            logError(e);
        } finally {
            closeDB(connection, statement, rs);
        }
        return syncIDDatas;
    }
    public void setSyncIDDatas(List<SyncIDData> syncIDDataList) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            synchronized (this){
                String sql = "replace into t_osnid(type,osnID,timeStamp,tip) values(?,?,?,?)";
                connection = comboPooledDataSource.getConnection();
                connection.setAutoCommit(false);
                statement = connection.prepareStatement(sql);
                for (SyncIDData id : syncIDDataList) {
                    statement.setInt(1,id.type);
                    statement.setString(2, id.osnID);
                    statement.setLong(3, id.timeStamp);
                    statement.setString(4, id.tip);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            }
        } catch (Exception e) {
            logError(e);
        } finally {
            closeDB(connection, statement, rs);
        }
    }
    public void setSyncIDData(int type, String tip, String osnID, long timeStamp) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            synchronized (this){
                String sql = "replace into t_osnid(type,osnID,timeStamp,tip) values(?,?,?,?)";
                connection = comboPooledDataSource.getConnection();
                statement = connection.prepareStatement(sql);
                statement.setInt(1, type);
                statement.setString(2, osnID);
                statement.setLong(3, timeStamp);
                statement.setString(4, tip);
                statement.executeUpdate();
            }
        } catch (Exception e) {
            logError(e);
        } finally {
            closeDB(connection, statement, rs);
        }
    }
}