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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class CassandraAppenderTest {

    private static final String KEYSPACE = "decanter";
    private static final String CASSANDRA_PORT = "9142";
    private static final String CASSANDRA_HOST = "localhost";
    private static final String TABLE_NAME = "decanter";
    private static final String TOPIC = "decanter/collect/jmx";
    private static final long TIMESTAMP = 1454428780634L;

    private static final Logger logger = LoggerFactory.getLogger(CassandraAppenderTest.class);
    private CassandraDaemon cassandraDaemon;
    
    @Before
    public void setUp() throws Exception {

        System.setProperty("cassandra-foreground", "false");
        System.setProperty("cassandra.boot_without_jna", "true");

        cassandraDaemon = new CassandraDaemon(true);
        logger.info("starting cassandra deamon");
        cassandraDaemon.init(null);
        cassandraDaemon.start();
        
        logger.info("cassandra up and runnign");
        
    }

    @After
    public void tearDown() throws Exception {
        Schema.instance.clear();
        logger.info("stopping cassandra");
        cassandraDaemon.stop();
        logger.info("destroying the cassandra deamon");
        cassandraDaemon.destroy();
        logger.info("cassandra is removed");
        cassandraDaemon = null;
    }

    @Test
    public void test() throws Exception {
        Marshaller marshaller = new JsonMarshaller();
        CassandraAppender appender = new CassandraAppender();
        Dictionary<String, Object> config = new Hashtable<String, Object>();
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
        
        Session session = getSession();
        
        ResultSet execute = session.execute("SELECT * FROM "+ KEYSPACE+"."+TABLE_NAME+";");
        List<Row> all = execute.all();
        Assert.assertEquals(1, all.size());
        assertThat(all, not(nullValue()));
        
        assertThat(all.get(0).getTimestamp("timeStamp").getTime(), is(TIMESTAMP));
        
        session.close();
    }

    @Test
    public void testWithFilter() throws Exception {
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

        Session session = getSession();

        ResultSet execute = session.execute("SELECT * FROM "+ KEYSPACE+"."+TABLE_NAME+";");
        List<Row> all = execute.all();
        Assert.assertEquals(1, all.size());
        assertThat(all, not(nullValue()));

        assertThat(all.get(0).getTimestamp("timeStamp").getTime(), is(TIMESTAMP));

        session.close();
    }

    private Session getSession() {
        Builder clusterBuilder = Cluster.builder().addContactPoint(CASSANDRA_HOST);
        clusterBuilder.withPort(Integer.valueOf(CASSANDRA_PORT));

        Cluster cluster = clusterBuilder.build();
        return cluster.connect();
    }
    
}
