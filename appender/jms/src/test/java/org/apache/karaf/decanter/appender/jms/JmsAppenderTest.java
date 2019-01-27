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
package org.apache.karaf.decanter.appender.jms;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

public class JmsAppenderTest {

    @Test
    public void test() throws JMSException {
        ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        JmsAppender appender = new JmsAppender();
        appender.connectionFactory = cf;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("message.type", "map");
        appender.activate(config);
        
        Connection con = cf.createConnection();
        con.start();
        Session sess = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        MessageConsumer consumer = sess.createConsumer(sess.createQueue("decanter"));
        
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("timestamp", 1l);
        props.put("string", "test");
        props.put("boolean", true);
        props.put("integer", 1);
        props.put("testnull", null);
        props.put("map", new HashMap<String, String>());
        appender.handleEvent(new Event("decanter/collect", props));
        
        MapMessage message = (MapMessage)consumer.receive(1000);
        consumer.close();
        sess.close();
        con.close();
        
        Assert.assertEquals(1l, message.getObject("timestamp"));
        Assert.assertEquals("test", message.getObject("string"));
        Assert.assertEquals(true, message.getObject("boolean"));
        Assert.assertEquals(1, message.getObject("integer"));
        Object map = message.getObject("map");
        Assert.assertTrue(map instanceof Map);
    }

    @Test
    public void testWithFilter() throws JMSException {
        ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        JmsAppender appender = new JmsAppender();
        appender.connectionFactory = cf;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("message.type", "map");
        config.put(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG, ".*refused.*");
        config.put(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG, ".*refused.*");
        appender.activate(config);

        Connection con = cf.createConnection();
        con.start();
        Session sess = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageConsumer consumer = sess.createConsumer(sess.createQueue("decanter"));

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("timestamp", 1l);
        data.put("string", "test");
        data.put("boolean", true);
        data.put("integer", 1);
        data.put("testnull", null);
        data.put("map", new HashMap<String, String>());
        appender.handleEvent(new Event("decanter/collect", data));

        data = new HashMap<>();
        data.put("refused_property", "value");
        appender.handleEvent(new Event("decanter/collect", data));

        data = new HashMap<>();
        data.put("property", "refused_value");
        appender.handleEvent(new Event("decanter/collect", data));

        MapMessage message = (MapMessage)consumer.receive(1000);
        consumer.close();
        sess.close();
        con.close();

        Assert.assertEquals(1l, message.getObject("timestamp"));
        Assert.assertEquals("test", message.getObject("string"));
        Assert.assertEquals(true, message.getObject("boolean"));
        Assert.assertEquals(1, message.getObject("integer"));
        Object map = message.getObject("map");
        Assert.assertTrue(map instanceof Map);
    }

}
