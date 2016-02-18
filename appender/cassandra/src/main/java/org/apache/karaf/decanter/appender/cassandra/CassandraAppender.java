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
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
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

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraAppender.class);

    private String cassandraHost;
    private Integer cassandraPort;
    private String cassandraUser;
    private String cassandraPassword;
    private String keyspace;
    private String tableName;
    private Marshaller marshaller;

    private final static String createTableTemplate = "CREATE TABLE IF NOT EXISTS TABLENAME (timeStamp timestamp PRIMARY KEY, content Text);";

    private final static String upsertQueryTemplate = "INSERT INTO TABLENAME(timeStamp, content) VALUES(?,?);";

    public CassandraAppender() {
    }
    
    public CassandraAppender(Marshaller marshaller, String keyspace, String tableName, String cassandraHost,
            Integer cassandraPort, String cassandraUser, String cassandraPassword) {
        this.marshaller = marshaller;
        this.keyspace = keyspace;
        this.tableName = tableName;
        this.cassandraHost = cassandraHost;
        this.cassandraPort = cassandraPort;
        this.cassandraUser = cassandraUser;
        this.cassandraPassword = cassandraPassword;
    }
    
    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        Dictionary<String, Object> config = context.getProperties();
        this.keyspace = getValue(config, "keyspace.name", "decanter");
        this.tableName = getValue(config, "table.name", "decanter");
        this.cassandraHost = getValue(config, "cassandra.host", "localhost");
        this.cassandraPort = Integer.parseInt(getValue(config, "cassandra.port", "9042"));
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.trace("Looking for the Cassandra datasource");
        try (Session session = createSession()){
            ResultSet execute;
            try {
                execute = session.execute("USE " + keyspace + ";");
            } catch (InvalidQueryException e) {
                session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };");
                session.execute("USE " + keyspace + ";");
            }

            execute = session.execute("select columnfamily_name from system.schema_columnfamilies where keyspace_name = '"+keyspace+"';");
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
                session.execute(createTableTemplate.replace("TABLENAME", tableName));
                LOGGER.debug("Table {} has been created", tableName);
            }

            Long timestamp = (Long) event.getProperty("timestamp");
            java.util.Date date = timestamp != null ? new java.util.Date(timestamp) : new java.util.Date();
            String jsonSt = marshaller.marshal(event);

            String upsertQuery = upsertQueryTemplate.replaceAll("TABLENAME", tableName);

            if (timestamp == null) {
                timestamp = System.currentTimeMillis();
            }
            session.execute(upsertQuery, timestamp, jsonSt);

            LOGGER.trace("Data inserted into {} table", tableName);
        } catch (Exception e) {
            LOGGER.error("Can't store in the database", e);
        }
    }

    private Session createSession() {
        Session session;
        Builder clusterBuilder = Cluster.builder().addContactPoint(cassandraHost);
        if (cassandraPort != null) {
            clusterBuilder.withPort(cassandraPort);
        }
        Cluster cluster = clusterBuilder.build();
        session = cluster.connect();
        return session;
    }
    
    @Reference
    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }
}
