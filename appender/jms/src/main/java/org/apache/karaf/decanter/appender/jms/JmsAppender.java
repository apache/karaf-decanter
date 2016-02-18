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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

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
    name = "org.apache.karaf.decanter.appender.jms",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class JmsAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(JmsAppender.class);

    private ConnectionFactory connectionFactory;
    private String username;
    private String password;
    private String destinationName;
    private String destinationType;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        Dictionary<String, Object> config = context.getProperties();
        username = getProperty(config, "username", null);
        password = getProperty(config, "password", null);
        destinationName = getProperty(config, "destination.name", "decanter");
        destinationType = getProperty(config, "destination.type", "queue");
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        Connection connection = null;
        Session session = null;
        try {
            connection = createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = createDestination(session);
            MessageProducer producer = session.createProducer(destination);
            Message message = session.createMapMessage();

            for (String name : event.getPropertyNames()) {
                Object value = event.getProperty(name);
                setProperty(message, name, value);
            }

            producer.send(message);
            producer.close();
        } catch (Exception e) {
            LOGGER.warn("Can't send to JMS broker", e);
        }
        finally {
            safeClose(session);
            safeClose(connection);
        }
    }

    private void setProperty(Message message, String name, Object value) throws JMSException {
        if (value instanceof String)
            message.setStringProperty(name, (String) value);
        else if (value instanceof Boolean)
            message.setBooleanProperty(name, (Boolean) value);
        else if (value instanceof Double)
            message.setDoubleProperty(name, (Double) value);
        else if (value instanceof Integer)
            message.setIntProperty(name, (Integer) value);
        else message.setStringProperty(name, value.toString());
        // we can setObject with List, Map, but they have to contain only primitives
    }

    private Destination createDestination(Session session) throws JMSException {
        Destination destination;
        if (destinationType.equalsIgnoreCase("topic")) {
            destination = session.createTopic(destinationName);
        } else {
            destination = session.createQueue(destinationName);
        }
        return destination;
    }

    private Connection createConnection() throws JMSException {
        Connection connection;
        if (username != null) {
            connection = connectionFactory.createConnection(username, password);
        } else {
            connection = connectionFactory.createConnection();
        }
        return connection;
    }

    public void safeClose(Session sess) {
        if (sess != null) {
            try {
                sess.close();
            } catch (JMSException e) {
                // Ignore
            }
        }
    }
    
    public void safeClose(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (JMSException e) {
                // Ignore
            }
        }
    }
    
    @Reference(target="(osgi.jndi.service.name=jms/decanter)")
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}
