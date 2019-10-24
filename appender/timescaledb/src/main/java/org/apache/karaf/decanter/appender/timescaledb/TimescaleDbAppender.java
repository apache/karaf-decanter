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
package org.apache.karaf.decanter.appender.timescaledb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Dictionary;
import javax.sql.DataSource;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
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
    name = "org.apache.karaf.decanter.appender.timescaledb",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class TimescaleDbAppender implements EventHandler {

    public static final String TABLE_NAME_PROPERTY = "table.name";

    public static final String TABLE_NAME_DEFAULT = "decanter";

    @Reference
    public Marshaller marshaller;

    @Reference
    public DataSource dataSource;

    private final static Logger LOGGER = LoggerFactory.getLogger(TimescaleDbAppender.class);

    private final static String createExtensionTemplate =
            "CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE";
    private final static String createTableQueryTemplate =
            "CREATE TABLE TABLENAME(timestamp BIGINT, content text)";
    private final static String convertHyperTableQueryTemplate =
            "SELECT * FROM create_hypertable('TABLENAME', 'timestamp', chunk_time_interval=>_timescaledb_internal.interval_to_usec('1 day'), migrate_data => true);";

    private final static String insertQueryTemplate =
            "INSERT INTO TABLENAME(timestamp, content) VALUES(?,?)";

    private Dictionary<String, Object> config;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) {
        this.config = config;
        String tableName = getValue(config, TABLE_NAME_PROPERTY, TABLE_NAME_DEFAULT);
        try (Connection connection = dataSource.getConnection()) {
            createStructure(connection);
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
        if (EventFilter.match(event, config)) {
            try (Connection connection = dataSource.getConnection()) {
                String tableName = getValue(config, TABLE_NAME_PROPERTY, TABLE_NAME_DEFAULT);
                String jsonSt = marshaller.marshal(event);
                String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", tableName);
                Long timestamp = (Long) event.getProperty(EventConstants.TIMESTAMP);
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
    }

    private void createStructure(Connection connection) {
        String tableName = getValue(config, TABLE_NAME_PROPERTY, TABLE_NAME_DEFAULT);
        String createTemplate = createTableQueryTemplate;
        String createTableQuery = createTemplate.replaceAll("TABLENAME", tableName);

        String convertTemplate = convertHyperTableQueryTemplate;
        String convertTableQuery = convertTemplate.replaceAll("TABLENAME", tableName);

        try (Statement createStatement = connection.createStatement()) {
            createStatement.executeUpdate(createExtensionTemplate);
            LOGGER.debug("Extension has been created", tableName);
            createStatement.executeUpdate(createTableQuery);
            LOGGER.debug("Table {} has been created", tableName);
            createStatement.execute(convertTableQuery);
            LOGGER.debug("Table {} has been converted to hypertable", tableName);
        } catch (SQLException e) {
            LOGGER.error("Can't create table {}", e);
        }
    }

}
