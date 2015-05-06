package org.apache.karaf.decanter.appender.jdbc;

import org.apache.commons.dbcp2.BasicDataSource;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

public class JdbcAppender implements EventHandler{

    private final Logger logger = LoggerFactory.getLogger(JdbcAppender.class);
    private BasicDataSource dataSource;
    private Connection connection;
    private PreparedStatement preparedStatement, createPreparedStatement;
    private int maxPreparedStatements;
    private boolean usePoolPreparedStatement;
    private String tableName, columnName1 , columnName2;

    private final String createTableQuery =
            "CREATE TABLE IF NOT EXISTS `?` (" +
            "`?` VARCHAR(255) NOT NULL," +
            "`?` LONGBLOB NOT NULL,";
    private final String insertQuery =
            "INSERT INTO `?`(?,?) VALUES('?','?')";

    public JdbcAppender(DataSource dataSource,boolean usePoolPreparedStatement,int maxPreparedStatements,String tableName, String columnName1, String columnName2){
        setDataSource((BasicDataSource)dataSource);
        getDataSource().setPoolPreparedStatements(getUsePoolPreparedStatement());
        if(getUsePoolPreparedStatement() == true) getDataSource().setMaxOpenPreparedStatements(getMaxPreparedStatements());
        setUsePoolPreparedStatement(usePoolPreparedStatement);
        setMaxPreparedStatements(maxPreparedStatements);
        setTableName(tableName);
        setColumnName1(columnName1);
        setColumnName2(columnName2);
    }

    @Override
    public void handleEvent(Event event) {
        try {
            connection = getDataSource().getConnection();
            createPreparedStatement = connection.prepareStatement(createTableQuery);
            createPreparedStatement.setString(0,getTableName());
            createPreparedStatement.setString(1,getColumnName1());
            createPreparedStatement.setString(2,getColumnName2());
            createPreparedStatement.executeUpdate(createTableQuery);

            logger.debug("Database table \"decanter\" has been created or already exists");

            for(String name : event.getPropertyNames())
            {
                preparedStatement = connection.prepareStatement(insertQuery);
                preparedStatement.setString(0,getTableName());
                preparedStatement.setString(1,getColumnName1());
                preparedStatement.setString(2,getColumnName2());
                preparedStatement.setString(3,name);
                preparedStatement.setObject(4, event.getProperty(name));
                preparedStatement.executeUpdate();
            }
            connection.commit();
            logger.debug("Data was inserted into \"decanter\" table.");
        } catch (SQLException e) {
            try {
                if(connection!= null) connection.rollback();
            } catch (SQLException e1) {
                logger.debug("An error occured and the rollback also failed.");
                logger.error(e.getMessage());
            }
            logger.error("An error occured during the JDBC appending , be sure the connection from the datasource \"jdbc-appender\" provided is right.");
        }finally {
            try { if (createPreparedStatement != null) createPreparedStatement.close(); } catch(Exception e) {logger.error("Problem closing the database creation Statement.");}
            try { if (preparedStatement != null) preparedStatement.close(); } catch(Exception e) {logger.error("Problem closing the PreparedStatement to insert data.");}
            try { if (connection != null) connection.close(); } catch(Exception e) {logger.error("Problem closing the Connection.");}
        }
    }

    public void close(){
    if(!getDataSource().isClosed()) try { getDataSource().close(); } catch (SQLException e) {logger.error("Datasource could not be closed.");}
    try { if (createPreparedStatement != null) createPreparedStatement.close(); } catch(Exception e) {logger.error("Problem closing the database creation Statement.");}
    try { if (preparedStatement != null) preparedStatement.close(); } catch(Exception e) {logger.error("Problem closing the PreparedStatement to insert data.");}
    try { if (connection != null) connection.close(); } catch(Exception e) {logger.error("Problem closing the Connection.");}
    }

    public void setMaxPreparedStatements(int maxPreparedStatements){ this.maxPreparedStatements = maxPreparedStatements;}

    public int getMaxPreparedStatements(){return this.maxPreparedStatements;}

    public void setUsePoolPreparedStatement(boolean usePoolPreparedStatement){this.usePoolPreparedStatement = usePoolPreparedStatement;}

    public boolean getUsePoolPreparedStatement(){return this.usePoolPreparedStatement;}

    public String getColumnName2() {return columnName2;}

    public void setColumnName2(String columnName2) {this.columnName2 = columnName2;}

    public String getColumnName1() {return columnName1;}

    public void setColumnName1(String columnName1) {this.columnName1 = columnName1;}

    public String getTableName() {return tableName;}

    public void setTableName(String tableName) {this.tableName = tableName;}

    public BasicDataSource getDataSource() {return dataSource;}

    public void setDataSource(BasicDataSource dataSource) {this.dataSource = dataSource;}
}
