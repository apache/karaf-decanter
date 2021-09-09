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
package org.apache.karaf.decanter.collector.jdbc;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.jdbc",
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = { "decanter.collector.name=jdbc",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-jdbc"}
)
public class JdbcCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public DataSource dataSource;

    private final static Logger LOGGER = LoggerFactory.getLogger(JdbcCollector.class);

    private String query;
    private String topic;
    private Dictionary<String, Object> properties;
    private Connection connection;
    private PreparedStatement preparedStatement;

    @Activate
    public void activate(ComponentContext context) throws Exception {
        properties = context.getProperties();
        activate(properties);
    }

    public void activate(Dictionary<String, Object> config)  throws Exception {
        query = getProperty(config, "query", null);
        if (query == null) {
            throw new IllegalStateException("Query is mandatory");
        }
        topic = getProperty(config, EventConstants.EVENT_TOPIC, "decanter/collect/jdbc");

        connection = dataSource.getConnection();
        preparedStatement = connection.prepareStatement(query);
    }

    @Deactivate
    public void deactivate() throws Exception {
        if (preparedStatement != null) {
            preparedStatement.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter JDBC collector exectutes query {}", query);

        List<Map<String, Object>> dataRows = query();

        for (Map<String, Object> data : dataRows) {
            Event event = new Event(topic, data);
            dispatcher.postEvent(event);
        }
    }

    public List<Map<String, Object>> query() {
        List<Map<String, Object>> dataRows = new ArrayList<>();

        try (ResultSet resultSet = preparedStatement.executeQuery()) {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columnCount = resultSetMetaData.getColumnCount();
            int rowId = 1;
            while (resultSet.next()) {
                Map<String, Object> data = new HashMap<>();
                data.put("type", "jdbc");
                // unfortunately, getRow() is not fully supported by all JDBC driver, like Derby
                // data.put("row", resultSet.getRow());
                data.put("rowId", rowId);
                rowId++;

                for (int i = 1; i <= columnCount; i++) {
                    String columnName = resultSetMetaData.getColumnName(i);
                    int columnType = resultSetMetaData.getColumnType(i);
                    if (columnType == Types.CHAR || columnType == Types.VARCHAR) {
                        String value = resultSet.getString(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.BOOLEAN) {
                        Boolean value = resultSet.getBoolean(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.INTEGER) {
                        Integer value = resultSet.getInt(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.CLOB) {
                        Clob value = resultSet.getClob(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.ARRAY) {
                        Array value = resultSet.getArray(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.FLOAT) {
                        Float value = resultSet.getFloat(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.BLOB) {
                        Blob value = resultSet.getBlob(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.DATE) {
                        Date value = resultSet.getDate(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.DOUBLE) {
                        Double value = resultSet.getDouble(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.BIGINT) {
                        Long value = resultSet.getLong(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.TIME) {
                        Time value = resultSet.getTime(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.TIMESTAMP) {
                        Timestamp value = resultSet.getTimestamp(i);
                        data.put(columnName, value);
                    }
                    if (columnType == Types.NUMERIC) {
                        BigDecimal value = resultSet.getBigDecimal(i);
                        data.put(columnName, value);
                    }
                }

                try {
                    PropertiesPreparator.prepare(data, properties);
                } catch (Exception e) {
                    LOGGER.warn("Can't prepare data for the dispatcher", e);
                }

                dataRows.add(data);
            }
        } catch (Exception e) {
            LOGGER.warn("Can't get data from database", e);
        }

        return dataRows;
    }

}
