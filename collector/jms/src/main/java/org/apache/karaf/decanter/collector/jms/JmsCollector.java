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
package org.apache.karaf.decanter.collector.jms;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.ByteArrayInputStream;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.jms",
        immediate = true
)
public class JmsCollector {

    @Reference
    public ConnectionFactory connectionFactory;

    @Reference
    public Unmarshaller unmarshaller;

    @Reference
    public EventAdmin dispatcher;

    private final static Logger LOGGER = LoggerFactory.getLogger(JmsCollector.class);

    private Dictionary<String, Object> properties;
    private String dispatcherTopic;
    private String username;
    private String password;
    private String destinationName;
    private String destinationType;

    private Connection connection;
    private Session session;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        properties = componentContext.getProperties();
        username = getProperty(properties, "username", null);
        password = getProperty(properties, "password", null);
        destinationName = getProperty(properties, "destination.name", "decanter");
        destinationType = getProperty(properties, "destination.type", "queue");
        dispatcherTopic = getProperty(properties, EventConstants.EVENT_TOPIC, "decanter/collect/jms/decanter");

        connection = createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = createDestination(session);
        MessageConsumer consumer = session.createConsumer(destination);
        consumer.setMessageListener(new DecanterMessageListener(dispatcher, unmarshaller));
        connection.start();
    }

    private Destination createDestination(Session session) throws JMSException {
        return (destinationType.equalsIgnoreCase("topic"))
                ? session.createTopic(destinationName)
                : session.createQueue(destinationName);
    }

    private Connection createConnection() throws JMSException {
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

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Deactivate
    public void deactivate() {
        safeClose(session);
        safeClose(connection);
    }

    public class DecanterMessageListener implements MessageListener {

        private EventAdmin dispatcher;
        private Unmarshaller unmarshaller;

        public DecanterMessageListener(EventAdmin dispatcher, Unmarshaller unmarshaller) {
            this.dispatcher = dispatcher;
            this.unmarshaller = unmarshaller;
        }

        @Override
        public void onMessage(Message message) {
            if (!(message instanceof MapMessage) && !(message instanceof TextMessage)) {
                LOGGER.warn("JMS is not a MapMessage or a TextMessage.");
                return;
            }

            if (message instanceof MapMessage) {
                MapMessage mapMessage = (MapMessage) message;

                try {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "jms");

                    Enumeration names = mapMessage.getMapNames();
                    while (names.hasMoreElements()) {
                        String name = (String) names.nextElement();
                        data.put(name, mapMessage.getObject(name));
                    }

                    PropertiesPreparator.prepare(data, properties);

                    Event event = new Event(dispatcherTopic, data);
                    dispatcher.postEvent(event);
                } catch (Exception e) {
                    LOGGER.warn("Can't process JMS message", e);
                }
            }
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;

                try {
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "jms");

                    ByteArrayInputStream is = new ByteArrayInputStream(textMessage.getText().getBytes());
                    data.putAll(unmarshaller.unmarshal(is));

                    PropertiesPreparator.prepare(data, properties);

                    Event event = new Event(dispatcherTopic, data);
                    dispatcher.postEvent(event);
                } catch (Exception e) {
                    LOGGER.warn("Can't process JMS message", e);
                }
            }
        }

    }

}
