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

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraAppenderTest {

    private static final String KEYSPACE = "decanter";
    private static final String CASSANDRA_PORT = "9042";
    private static final String CASSANDRA_HOST = "localhost";
    private static final String TABLE_NAME = "decanter";
    private static final String TOPIC = "decanter/collect/jmx";
    private static final long TIMESTAMP = 1454428780634L;

    private static final Logger logger = LoggerFactory.getLogger(CassandraAppenderTest.class);
    private static CassandraDaemon cassandraDaemon;
    
    @BeforeClass
    public static void setUp() throws Exception {

        System.setProperty("cassandra.boot_without_jna", "true");
        System.setProperty("cassandra.storagedir", "target/data/cassandra/embedded");

        cassandraDaemon = new CassandraDaemon(false);
        logger.info("starting cassandra daemon");
        cassandraDaemon.activate();
        logger.info("cassandra up and running");
        CqlSession session = getSession();
        session.execute(
                "CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE
                        + " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };");
        session.close();
        logger.info("default Keyspace 'decanter' created");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Schema.instance.clear();
        logger.info("stopping cassandra");
        cassandraDaemon.stop();
        logger.info("destroying the cassandra daemon");
        cassandraDaemon.destroy();
        logger.info("cassandra is removed");
        cassandraDaemon = null;
    }

    @Test
    public void test() {
        Marshaller marshaller = new JsonMarshaller();
        CassandraAppender appender = new CassandraAppender();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(CassandraAppender.CASSANDRA_PORT_PROPERTY, CASSANDRA_HOST);
        config.put(CassandraAppender.CASSANDRA_PORT_PROPERTY, CASSANDRA_PORT);
        config.put(CassandraAppender.KEYSPACE_PROPERTY, KEYSPACE);
        config.put(CassandraAppender.TABLE_PROPERTY, TABLE_NAME);
        appender.marshaller = marshaller;
        appender.activate(config);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put(EventConstants.TIMESTAMP, TIMESTAMP);
        Event event = new Event(TOPIC, properties);
        
        appender.handleEvent(event);
        appender.deactivate();

        CqlSession session = getSession();

        ResultSet execute = session.execute("SELECT * FROM "+ KEYSPACE+"."+TABLE_NAME+";");
        List<Row> all = execute.all();
        Assert.assertEquals(1, all.size());
        assertThat(all, not(nullValue()));
        
        assertThat(all.get(0).getInstant("timeStamp").toEpochMilli(), is(TIMESTAMP));
        
        session.close();
    }

    @Test
    public void testWithFilter() {
        Marshaller marshaller = new JsonMarshaller();
        CassandraAppender appender = new CassandraAppender();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(CassandraAppender.CASSANDRA_PORT_PROPERTY, CASSANDRA_HOST);
        config.put(CassandraAppender.CASSANDRA_PORT_PROPERTY, CASSANDRA_PORT);
        config.put(CassandraAppender.KEYSPACE_PROPERTY, KEYSPACE);
        config.put(CassandraAppender.TABLE_PROPERTY, TABLE_NAME);
        config.put(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG, ".*refused.*");
        config.put(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG, ".*refused.*");
        appender.marshaller = marshaller;
        appender.activate(config);

        Map<String, Object> data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("this is a refused property", "value");
        Event event = new Event(TOPIC, data);

        appender.handleEvent(event);

        data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("property", "this is a refused value");
        event = new Event(TOPIC, data);

        appender.handleEvent(event);

        data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("accepted", "accepted");
        event = new Event(TOPIC, data);

        appender.handleEvent(event);
        appender.deactivate();

        CqlSession session = getSession();

        ResultSet execute = session.execute("SELECT * FROM "+ KEYSPACE+"."+TABLE_NAME+";");
        List<Row> all = execute.all();
        Assert.assertEquals(1, all.size());
        assertThat(all, not(nullValue()));

        assertThat(all.get(0).getInstant("timeStamp").toEpochMilli(), is(TIMESTAMP));

        session.close();
    }

    private static CqlSession getSession() {

        DriverConfigLoader loader =
                DriverConfigLoader.programmaticBuilder()
                        .withStringList(DefaultDriverOption.CONTACT_POINTS, Arrays.asList(CASSANDRA_HOST + ":" + CASSANDRA_PORT))
                        .withString(DefaultDriverOption.PROTOCOL_VERSION, "V3")
                        .withString(DefaultDriverOption.PROTOCOL_MAX_FRAME_LENGTH, "256 MB")
                        .withString(DefaultDriverOption.SESSION_NAME, "decanter")
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
        return CqlSession.builder()
                .withClassLoader(CqlSession.class.getClassLoader())
                .withConfigLoader(loader)
                .withLocalDatacenter("datacenter1").build();
    }
    
}
