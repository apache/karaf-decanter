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

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;

public class TestJdbcAppender {
    private static final String TABLE_NAME = "decanter";
    private static final String TOPIC = "decanter/collect/jmx";
    private static final long TIMESTAMP = 1454428780634L;

    @Test
    public void testHandleEvent() throws SQLException {
        System.setProperty("derby.stream.error.file", "target/derby.log");
        Marshaller marshaller = new JsonMarshaller();
        EmbeddedDataSource dataSource = new EmbeddedDataSource();
        dataSource.setDatabaseName("target/testDB");
        dataSource.setCreateDatabase("create");
        
        deleteTable(dataSource);
        
        JdbcAppender appender = new JdbcAppender();
        appender.marshaller = marshaller;
        appender.dataSource = dataSource;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("dialect", "derby");
        appender.open(config);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put(EventConstants.TIMESTAMP, TIMESTAMP);
        Event event = new Event(TOPIC, properties);
        appender.handleEvent(event);

        try (Connection con = dataSource.getConnection(); Statement statement = con.createStatement();) {
            ResultSet res = statement.executeQuery("select timestamp, content from " + TABLE_NAME);
            res.next();
            long dbTimeStamp = res.getLong(1);
            String json = res.getString(2);
            JsonReader reader = Json.createReader(new StringReader(json));
            JsonObject jsonO = reader.readObject();
            Assert.assertEquals("Timestamp db", TIMESTAMP, dbTimeStamp);
            Assert.assertEquals("Timestamp string", "2016-02-02T15:59:40,634Z",jsonO.getString("@timestamp"));
            Assert.assertEquals("timestamp long", TIMESTAMP, jsonO.getJsonNumber(EventConstants.TIMESTAMP).longValue());
            Assert.assertEquals("Topic", TOPIC, jsonO.getString(EventConstants.EVENT_TOPIC.replace('.','_')));
            Assert.assertFalse(res.next());
        }
    }

    private void deleteTable(EmbeddedDataSource dataSource) throws SQLException {
        try (Connection con = dataSource.getConnection(); Statement statement = con.createStatement();) {
            statement.execute("delete from " + TABLE_NAME);
        } catch (Exception e) {
            // Ignore
        }
    }

}
