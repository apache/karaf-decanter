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
package org.apache.karaf.decanter.appender.cassandra;

import java.util.Dictionary;
import java.util.List;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;

@Component(
    name = "org.apache.karaf.decanter.appender.cassandra",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class CassandraAppender implements EventHandler {

    public static final String KEYSPACE_PROPERTY = "keyspace.name";
    public static final String TABLE_PROPERTY = "table.name";
    public static final String CASSANDRA_HOST_PROPERTY = "cassandra.host";
    public static final String CASSANDRA_PORT_PROPERTY = "cassandra.port";

    public static final String KEYSPACE_DEFAULT = "decanter";
    public static final String TABLE_DEFAULT = "decanter";
    public static final String CASSANDRA_HOST_DEFAULT = "localhost";
    public static final String CASSANDRA_PORT_DEFAULT = "9042";

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraAppender.class);

    private Dictionary<String, Object> config;

    @Reference
    public Marshaller marshaller;

    private final static String createTableTemplate = "CREATE TABLE IF NOT EXISTS %s (timeStamp timestamp PRIMARY KEY, content Text);";

    private final static String insertTemplate = "INSERT INTO %s (timeStamp, content) VALUES(?,?);";

    private Cluster cluster;

    public CassandraAppender() {
    }
    
    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        Dictionary<String, Object> config = context.getProperties();
        activate(config);
    }

    void activate(Dictionary<String, Object> config) {
        this.config = config;
        String host = getValue(config, CASSANDRA_HOST_PROPERTY, CASSANDRA_HOST_DEFAULT);
        Integer port = Integer.parseInt(getValue(config, CASSANDRA_PORT_PROPERTY, CASSANDRA_PORT_DEFAULT));
        Builder clusterBuilder = Cluster.builder().addContactPoint(host);
        if (port != null) {
            clusterBuilder.withPort(port);
        }
        cluster = clusterBuilder.build();
    }
    
    @Deactivate
    public void deactivate() {
        cluster.close();
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            LOGGER.trace("Looking for the Cassandra datasource");
            try (Session session = cluster.connect()) {
                String keyspace = getValue(config, KEYSPACE_PROPERTY, KEYSPACE_DEFAULT);
                String tableName = getValue(config, TABLE_PROPERTY, TABLE_DEFAULT);
                useKeyspace(session, keyspace);
                createTable(session, keyspace, tableName);

                Long timestamp = (Long) event.getProperty("timestamp");
                if (timestamp == null) {
                    timestamp = System.currentTimeMillis();
                }
                String jsonSt = marshaller.marshal(event);
                session.execute(String.format(insertTemplate, tableName), timestamp, jsonSt);

                LOGGER.trace("Data inserted into {} table", tableName);
            } catch (Exception e) {
                LOGGER.error("Can't store in the database", e);
            }
        }
    }

    private static void useKeyspace(Session session, String keyspace) {
        try {
            session.execute("USE " + keyspace + ";");
        } catch (InvalidQueryException e) {
            session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };");
            session.execute("USE " + keyspace + ";");
        }
    }

    private static void createTable(Session session, String keyspace, String tableName) {
        ResultSet execute = session.execute("select columnfamily_name from system.schema_columnfamilies where keyspace_name = '"+keyspace+"';");
        List<Row> all = execute.all();
        boolean found = false;
        for(Row row : all) {
            String table = row.getString("columnfamily_name");
            if (table.equalsIgnoreCase(tableName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            session.execute(String.format(createTableTemplate, tableName));
            LOGGER.debug("Table {} has been created", tableName);
        }
    }

}
