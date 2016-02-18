/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.appender.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Dictionary;

import javax.sql.DataSource;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    name = "org.apache.karaf.decanter.appender.jdbc",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class JdbcAppender implements EventHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(JdbcAppender.class);

    private final static String createTableQueryGenericTemplate =
            "CREATE TABLE TABLENAME(timestamp BIGINT, content VARCHAR(8192))";
    private final static String createTableQueryMySQLTemplate =
            "CREATE TABLE TABLENAME(timestamp BIGINT, content CLOB)";
    private final static String createTableQueryDerbyTemplate =
            "CREATE TABLE TABLENAME(timestamp BIGINT, content CLOB)";

    private final static String insertQueryTemplate =
            "INSERT INTO TABLENAME(timestamp, content) VALUES(?,?)";

    private Marshaller marshaller;
    private DataSource dataSource;
    
    String tableName;
    String dialect;
    
    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) {
        this.tableName = getValue(config, "table.name", "decanter");
        this.dialect = getValue(config, "dialect", "generic");
        try (Connection connection = dataSource.getConnection()) {
            createTable(connection);
        } catch (Exception e) {
            LOGGER.debug("Error creating table " + tableName, e);
        } 
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        try (Connection connection = dataSource.getConnection()) {
            String jsonSt = marshaller.marshal(event);
            String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", tableName);
            Long timestamp = (Long)event.getProperty(EventConstants.TIMESTAMP);
            if (timestamp == null) {
                timestamp = System.currentTimeMillis();
            }
            try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                insertStatement.setLong(1, timestamp);
                insertStatement.setString(2, jsonSt);
                insertStatement.executeUpdate();
                LOGGER.trace("Data inserted into {} table", tableName);
            }
        } catch (Exception e) {
            LOGGER.error("Can't store in the database", e);
        }
    }

    private void createTable(Connection connection) {
        String createTemplate = null;
        if (dialect.equalsIgnoreCase("mysql")) {
            createTemplate = createTableQueryMySQLTemplate;
        } else if (dialect.equalsIgnoreCase("derby")) {
            createTemplate = createTableQueryDerbyTemplate;
        } else {
            createTemplate = createTableQueryGenericTemplate;
        }
        String createTableQuery = createTemplate.replaceAll("TABLENAME", tableName);
        try (Statement createStatement = connection.createStatement()) {
            createStatement.executeUpdate(createTableQuery);
            LOGGER.debug("Table {} has been created", tableName);
        } catch (SQLException e) {
            LOGGER.trace("Can't create table {}", e);
        }
    }
    
    @Reference
    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    @Reference(target="(osgi.jndi.service.name=jdbc/decanter)")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
