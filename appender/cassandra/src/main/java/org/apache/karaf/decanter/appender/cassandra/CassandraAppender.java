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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;

@Component(
    name = "org.apache.karaf.decanter.appender.cassandra",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class CassandraAppender implements EventHandler {

    public static String KEYSPACE_PROPERTY = "keyspace.name";
    public static String TABLE_PROPERTY = "table.name";
    public static String CASSANDRA_HOST_PROPERTY = "cassandra.host";
    public static String CASSANDRA_PORT_PROPERTY = "cassandra.port";

    public static String KEYSPACE_DEFAULT = "decanter";
    public static String TABLE_DEFAULT = "decanter";
    public static String CASSANDRA_HOST_DEFAULT = "localhost";
    public static String CASSANDRA_PORT_DEFAULT = "9042";

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraAppender.class);

    private Dictionary<String, Object> config;

    @Reference
    public Marshaller marshaller;

    private final static String createTableTemplate = "CREATE TABLE IF NOT EXISTS %s (timeStamp timestamp PRIMARY KEY, content Text);";

    private CqlSession session;

    private String keyspace;

    private String tableName;

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
        this.keyspace = getValue(config, KEYSPACE_PROPERTY, KEYSPACE_DEFAULT);
        this.tableName = getValue(config, TABLE_PROPERTY, TABLE_DEFAULT);

        DriverConfigLoader loader =
                DriverConfigLoader.programmaticBuilder()
                        .withStringList(DefaultDriverOption.CONTACT_POINTS, Arrays.asList(host + ":" + port))
                        .withString(DefaultDriverOption.PROTOCOL_VERSION, "V3")
                        .withString(DefaultDriverOption.PROTOCOL_MAX_FRAME_LENGTH, "256 MB")
                        .withString(DefaultDriverOption.SESSION_NAME, "decanter")
                        .withString(DefaultDriverOption.SESSION_KEYSPACE, keyspace)
                        .withString(DefaultDriverOption.CONFIG_RELOAD_INTERVAL, "0")
                        .withString(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, "500 milliseconds")
                        .withString(DefaultDriverOption.CONNECTION_SET_KEYSPACE_TIMEOUT, "500 milliseconds")
                        .withString(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_INTERVAL, "200 milliseconds")
                        .withString(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_TIMEOUT, "10 seconds")
                        .withBoolean(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_WARN, true)
                        .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, 1)
                        .withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE, 1)
                        .withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, 1024)
                        .withInt(DefaultDriverOption.CONNECTION_MAX_ORPHAN_REQUESTS, 24576)
                        .withString(DefaultDriverOption.HEARTBEAT_INTERVAL, "30 seconds")
                        .withString(DefaultDriverOption.HEARTBEAT_TIMEOUT, "500 milliseconds")
                        .withString(DefaultDriverOption.COALESCER_INTERVAL, "10 microseconds")
                        .withInt(DefaultDriverOption.COALESCER_MAX_RUNS, 5)
                        .withString(DefaultDriverOption.RECONNECTION_POLICY_CLASS, "ExponentialReconnectionPolicy")
                        .withString(DefaultDriverOption.RECONNECTION_BASE_DELAY, "1 second")
                        .withString(DefaultDriverOption.RECONNECTION_MAX_DELAY, "60 seconds")
                        .withBoolean(DefaultDriverOption.RECONNECT_ON_INIT, true)
                        .withString(DefaultDriverOption.LOAD_BALANCING_POLICY, "")
                        .withString(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS, "DefaultLoadBalancingPolicy")
                        .withString(DefaultDriverOption.RETRY_POLICY, "")
                        .withString(DefaultDriverOption.RETRY_POLICY_CLASS, "DefaultRetryPolicy")
                        .withString(DefaultDriverOption.SPECULATIVE_EXECUTION_POLICY, "")
                        .withString(DefaultDriverOption.SPECULATIVE_EXECUTION_POLICY_CLASS, "NoSpeculativeExecutionPolicy")
                        .withString(DefaultDriverOption.ADDRESS_TRANSLATOR_CLASS, "PassThroughAddressTranslator")
                        .withString(DefaultDriverOption.METADATA_SCHEMA_CHANGE_LISTENER_CLASS, "NoopSchemaChangeListener")
                        .withString(DefaultDriverOption.METADATA_NODE_STATE_LISTENER_CLASS, "NoopNodeStateListener")
                        .withString(DefaultDriverOption.REQUEST_TRACKER_CLASS, "NoopRequestTracker")
                        .withString(DefaultDriverOption.REQUEST_THROTTLER_CLASS, "PassThroughRequestThrottler")
                        .withBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE, false)
                        .withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_ONE")
                        .withInt(DefaultDriverOption.REQUEST_PAGE_SIZE, 5000)
                        .withString(DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY, "SERIAL")
                        .withString(DefaultDriverOption.TIMESTAMP_GENERATOR_CLASS, "AtomicTimestampGenerator")
                        .withBoolean(DefaultDriverOption.SOCKET_TCP_NODELAY, true)
                        .withString(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, "500 milliseconds")
                        .withInt(DefaultDriverOption.NETTY_IO_SIZE, 0)
                        .withInt(DefaultDriverOption.NETTY_IO_SHUTDOWN_QUIET_PERIOD, 2)
                        .withInt(DefaultDriverOption.NETTY_IO_SHUTDOWN_TIMEOUT, 15)
                        .withString(DefaultDriverOption.NETTY_IO_SHUTDOWN_UNIT, "SECONDS")
                        .withInt(DefaultDriverOption.NETTY_ADMIN_SIZE, 2)
                        .withInt(DefaultDriverOption.NETTY_ADMIN_SHUTDOWN_QUIET_PERIOD, 2)
                        .withInt(DefaultDriverOption.NETTY_ADMIN_SHUTDOWN_TIMEOUT, 15)
                        .withString(DefaultDriverOption.NETTY_ADMIN_SHUTDOWN_UNIT, "SECONDS")
                        .withString(DefaultDriverOption.NETTY_TIMER_TICK_DURATION, "100 milliseconds")
                        .withInt(DefaultDriverOption.NETTY_TIMER_TICKS_PER_WHEEL, 2048)
                        .withBoolean(DefaultDriverOption.METADATA_SCHEMA_ENABLED, true)
                        .withString(DefaultDriverOption.METADATA_SCHEMA_WINDOW, "1 second")
                        .withInt(DefaultDriverOption.METADATA_SCHEMA_MAX_EVENTS, 20)
                        .withString(DefaultDriverOption.METADATA_SCHEMA_REQUEST_TIMEOUT, "500 milliseconds")
                        .withInt(DefaultDriverOption.METADATA_SCHEMA_REQUEST_PAGE_SIZE, 5000)
                        .withBoolean(DefaultDriverOption.METADATA_TOKEN_MAP_ENABLED, true)
                        .withString(DefaultDriverOption.METADATA_TOPOLOGY_WINDOW,"1 second")
                        .withInt(DefaultDriverOption.METADATA_TOPOLOGY_MAX_EVENTS,20)
                        .withStringList(DefaultDriverOption.METRICS_SESSION_ENABLED, new ArrayList<>())
                        .withStringList(DefaultDriverOption.METRICS_NODE_ENABLED, new ArrayList<>())
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(5))
                        .build();
        session = CqlSession.builder()
                .withClassLoader(CqlSession.class.getClassLoader())
                .withConfigLoader(loader)
                .withLocalDatacenter("datacenter1").build();
        useKeyspace(session, keyspace);
        createTable(session, keyspace, tableName);
    }
    
    @Deactivate
    public void deactivate() {
        session.close();
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            LOGGER.trace("Looking for the Cassandra datasource");
            try {
                Long timestamp = (Long) event.getProperty("timestamp");
                if (timestamp == null) {
                    timestamp = System.currentTimeMillis();
                }
                String jsonSt = marshaller.marshal(event);
                Statement stmt =
                    insertInto(keyspace, tableName)
                            .value("timestamp", literal(timestamp))
                            .value("content", literal(jsonSt))
                            .build();
                session.execute(stmt);

                LOGGER.trace("Data inserted into {} table", tableName);
            } catch (Exception e) {
                LOGGER.error("Can't store in the database", e);
            }
        }
    }

    private static void useKeyspace(CqlSession session, String keyspace) {
        try {
            session.execute("USE " + keyspace + ";");
        } catch (InvalidQueryException e) {
            session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace + " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };");
            session.execute("USE " + keyspace + ";");
        }
    }

    private static void createTable(CqlSession session, String keyspace, String tableName) {
        ResultSet execute = session.execute("select table_name from system_schema.tables where keyspace_name = '"+keyspace+"';");
        List<Row> all = execute.all();
        boolean found = false;
        for(Row row : all) {
            String table = row.getString("table_name");
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
