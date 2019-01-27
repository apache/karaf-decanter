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
import java.util.Map;

import javax.jms.*;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
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

    public static String USERNAME_PROPERTY = "username";
    public static String PASSWORD_PROPERTY = "password";
    public static String DESTINATION_NAME_PROPERTY = "destination.name";
    public static String DESTINATION_TYPE_PROPERTY = "destination.type";
    public static String MESSAGE_TYPE_PROPERTY = "message.type";

    public static String USERNAME_DEFAULT = null;
    public static String PASSWORD_DEFAULT = null;
    public static String DESTINATION_NAME_DEFAULT = "decanter";
    public static String DESTINATION_TYPE_DEFAULT = "queue";
    public static String MESSAGE_TYPE_DEFAULT = "text";

    @Reference
    public ConnectionFactory connectionFactory;

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(JmsAppender.class);

    private Dictionary<String, Object> config;


    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        activate(context.getProperties());
    }
    
    void activate(Dictionary<String, Object> config) {
        this.config = config;
        LOGGER.info("Decanter JMS Appender started sending to {} {}", getValue(config, DESTINATION_TYPE_PROPERTY, DESTINATION_TYPE_DEFAULT), getValue(config, DESTINATION_NAME_PROPERTY, DESTINATION_NAME_DEFAULT));
    }

    private String getValue(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            Connection connection = null;
            Session session = null;
            try {
                connection = createConnection();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = createDestination(session);
                MessageProducer producer = session.createProducer(destination);
                if (getValue(config, MESSAGE_TYPE_PROPERTY, MESSAGE_TYPE_DEFAULT).equalsIgnoreCase("text")) {
                    TextMessage message = session.createTextMessage(marshaller.marshal(event));
                    producer.send(message);
                } else {
                    MapMessage message = session.createMapMessage();
                    for (String name : event.getPropertyNames()) {
                        Object value = event.getProperty(name);
                        setProperty(message, name, value);
                    }
                    producer.send(message);
                }
                producer.close();
            } catch (Exception e) {
                LOGGER.warn("Can't send to JMS broker", e);
            } finally {
                safeClose(session);
                safeClose(connection);
            }
        }
    }

    private void setProperty(MapMessage message, String name, Object value) throws JMSException {
        if (value == null) {
            return;
        }
        if (value instanceof String) {
            message.setString(name, (String) value);
        } else if (value instanceof Boolean) {
            message.setBoolean(name, (Boolean) value);
        } else if (value instanceof Double) {
            message.setDouble(name, (Double) value);
        } else if (value instanceof Integer) {
            message.setInt(name, (Integer) value);
        } else if (value instanceof Long) {
            message.setLong(name, (Long) value);
        } else if (value instanceof Map) {
            // Must only contain primitives
            message.setObject(name, value);
        } else {
            message.setString(name, value.toString());
        }
    }

    private Destination createDestination(Session session) throws JMSException {
        String destinationType = getValue(config, DESTINATION_TYPE_PROPERTY, DESTINATION_TYPE_DEFAULT);
        String destinationName = getValue(config, DESTINATION_NAME_PROPERTY, DESTINATION_NAME_DEFAULT);
        return (destinationType.equalsIgnoreCase("topic"))
            ? session.createTopic(destinationName)
            : session.createQueue(destinationName);
    }

    private Connection createConnection() throws JMSException {
        String username = getValue(config, USERNAME_PROPERTY, USERNAME_DEFAULT);
        String password = getValue(config, PASSWORD_PROPERTY, PASSWORD_DEFAULT);
        return (username != null) 
            ? connectionFactory.createConnection(username, password)
            : connectionFactory.createConnection();
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

}
