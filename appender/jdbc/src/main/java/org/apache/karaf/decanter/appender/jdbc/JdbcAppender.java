package org.apache.karaf.decanter.appender.jdbc;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

public class JdbcAppender implements EventHandler{

    private final Logger logger = LoggerFactory.getLogger(JdbcAppender.class);
    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private PreparedStatement preparedStatement;
    private final String createQuery =
            "CREATE TABLE IF NOT EXISTS `decanter` (" +
            "`event_name` VARCHAR(255) NOT NULL," +
            "`event_value` LONGBLOB NOT NULL," +
            "PRIMARY KEY (`event_name`));";
    private final String insertQuery = "INSERT INTO `decanter`(event_name,event_value) VALUES('?','?')";


    public JdbcAppender(DataSource dataSource){
        this.dataSource = dataSource;
    }

    @Override
    public void handleEvent(Event event) {
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            statement.executeUpdate(createQuery);
            logger.debug("Database table \"decanter\" has been created or already exists");
            for(String name : event.getPropertyNames())
            {
                preparedStatement = connection.prepareStatement(insertQuery);
                preparedStatement.setString(0,name);
                preparedStatement.setObject(1, event.getProperty(name));
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
            try { if (statement != null) statement.close(); } catch(Exception e) {logger.error("Problem closing the database creation Statement.");}
            try { if (preparedStatement != null) preparedStatement.close(); } catch(Exception e) {logger.error("Problem closing the PreparedStatement to insert data.");}
            try { if (connection != null) connection.close(); } catch(Exception e) {logger.error("Problem closing the Connection.");}
        }
    }
}
