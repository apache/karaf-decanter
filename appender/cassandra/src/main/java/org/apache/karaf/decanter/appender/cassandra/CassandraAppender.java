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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;

public class CassandraAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraAppender.class);

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");

    private String cassandraHost;
    private Integer cassandraPort;
    private String cassandraUser;
    private String cassandraPassword;
    private String keyspace;
    private String tableName;
    private Marshaller marshaller;

    private final static String createTableTemplate = "CREATE TABLE IF NOT EXISTS TABLENAME (timeStamp timestamp PRIMARY KEY, content Text);";

    private final static String upsertQueryTemplate = "INSERT INTO TABLENAME(timeStamp, content) VALUES(?,?);";

    public CassandraAppender(Marshaller marshaller, String keyspace, String tableName, String cassandraHost, Integer cassandraPort) {
        this(marshaller, keyspace, tableName, cassandraHost, cassandraPort, null, null);
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

    private int getRank(ServiceReference<?> reference) {
        Object rankObj = reference.getProperty(Constants.SERVICE_RANKING);
        // If no rank, then spec says it defaults to zero.
        rankObj = (rankObj == null) ? new Integer(0) : rankObj;
        // If rank is not Integer, then spec says it defaults to zero.
        return (rankObj instanceof Integer) ? (Integer) rankObj : 0;
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.trace("Looking for the Cassandra datasource");
        Session session = null;
        try {
            Builder clusterBuilder = Cluster.builder().addContactPoint(cassandraHost);
            if (cassandraPort != null) {
                clusterBuilder.withPort(cassandraPort);
            }
            Cluster cluster = clusterBuilder.build();
            session = cluster.connect();

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
        } finally {
            try {
                if (session != null)
                    session.close();
            } catch (Exception e) {
                // nothing to do
            }
        }
    }
}
