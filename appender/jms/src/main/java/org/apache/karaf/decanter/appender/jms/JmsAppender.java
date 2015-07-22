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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;

public class JmsAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(JmsAppender.class);

    private String connectionFactoryName;
    private String username;
    private String password;
    private String destinationName;
    private String destinationType;
    private BundleContext bundleContext;

    public JmsAppender(BundleContext bundleContext, String connectionFactoryName, String username, String password, String destinationName, String destinationType) {
        this.bundleContext = bundleContext;
        this.connectionFactoryName = connectionFactoryName;
        this.username = username;
        this.password = password;
        this.destinationName = destinationName;
        this.destinationType = destinationType;
    }

    private ServiceReference lookupConnectionFactory(String name) throws Exception {
        ServiceReference[] references = bundleContext.getServiceReferences(ConnectionFactory.class.getName(), "(|(osgi.jndi.service.name=" + name + ")(name=" + name + ")(service.id=" + name + "))");
        if (references == null || references.length == 0) {
            throw new IllegalArgumentException("No JMS connection factory found for " + name);
        }
        if (references.length > 1) {
            throw new IllegalArgumentException("Multiple JMS connection factories found for " + name);
        }
        return references[0];
    }


    @Override
    public void handleEvent(Event event) {
        LOGGER.debug("Looking for the JMS connection factory");
        ServiceReference reference;
        try {
            reference = lookupConnectionFactory(connectionFactoryName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Can't lookup for JMS connection for " + connectionFactoryName);
        }

        Connection connection = null;
        Session session = null;
        try {
            ConnectionFactory connectionFactory = (ConnectionFactory) bundleContext.getService(reference);
            if (username != null) {
                connection = connectionFactory.createConnection(username, password);
            } else {
                connection = connectionFactory.createConnection();
            }
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination;
            if (destinationType.equalsIgnoreCase("topic")) {
                destination = session.createTopic(destinationName);
            } else {
                destination = session.createQueue(destinationName);
            }
            MessageProducer producer = session.createProducer(destination);
            Message message = session.createMapMessage();

            for (String name : event.getPropertyNames()) {
                Object value = event.getProperty(name);
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

            producer.send(message);
            producer.close();
        } catch (Exception e) {
            LOGGER.warn("Can't send to JMS broker", e);
        }
        finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    // nothing to do
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // nothing to do
                }
            }
            if (reference != null) {
                bundleContext.ungetService(reference);
            }
        }
    }

}
