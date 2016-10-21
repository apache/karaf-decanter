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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.karaf.decanter.marshaller.json.JsonUnmarshaller;
import org.junit.*;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

public class JmsCollectorTest {

    private static BrokerService broker;

    private DispatcherMock dispatcher;
    private ActiveMQConnectionFactory connectionFactory;

    @BeforeClass
    public static void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setPersistenceAdapter(new MemoryPersistenceAdapter());
        broker.addConnector("tcp://localhost:61616");
        broker.setUseJmx(false);
        broker.start();
    }

    @Before
    public void setup() throws Exception {
        connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL("tcp://localhost:61616");

        JsonUnmarshaller unmarshaller = new JsonUnmarshaller();

        dispatcher = new DispatcherMock();

        JmsCollector jmsCollector = new JmsCollector();
        jmsCollector.setUnmarshaller(unmarshaller);
        jmsCollector.setConnectionFactory(connectionFactory);
        jmsCollector.setDispatcher(dispatcher);

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("destination.name", "decanter");
        componentContext.getProperties().put("destination.type", "queue");

        jmsCollector.activate(componentContext);
    }

    @Test
    public void test() throws Exception {
        Connection connection = null;
        Session session = null;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue("decanter"));
            ActiveMQMapMessage mapMessage = new ActiveMQMapMessage();
            mapMessage.setString("message", "map");
            producer.send(mapMessage);

            Thread.sleep(200L);

            Assert.assertEquals(1, dispatcher.getPostEvents().size());
            Event event = dispatcher.getPostEvents().get(0);
            Assert.assertEquals("map", event.getProperty("message"));
            Assert.assertEquals("jms", event.getProperty("type"));

            ActiveMQTextMessage textMessage = new ActiveMQTextMessage();
            textMessage.setText("{ \"message\" : \"text\" }");
            producer.send(textMessage);

            Thread.sleep(200L);

            Assert.assertEquals(2, dispatcher.getPostEvents().size());
            event = dispatcher.getPostEvents().get(1);
            Assert.assertEquals("text", event.getProperty("message"));
            Assert.assertEquals("jms", event.getProperty("type"));
        } finally {
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    @AfterClass
    public static void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    private class ComponentContextMock implements ComponentContext {

        private Properties properties;

        public ComponentContextMock() {
            this.properties = new Properties();
        }

        @Override
        public Dictionary getProperties() {
            return this.properties;
        }

        @Override
        public Object locateService(String s) {
            return null;
        }

        @Override
        public Object locateService(String s, ServiceReference serviceReference) {
            return null;
        }

        @Override
        public Object[] locateServices(String s) {
            return new Object[0];
        }

        @Override
        public BundleContext getBundleContext() {
            return null;
        }

        @Override
        public Bundle getUsingBundle() {
            return null;
        }

        @Override
        public ComponentInstance getComponentInstance() {
            return null;
        }

        @Override
        public void enableComponent(String s) {

        }

        @Override
        public void disableComponent(String s) {

        }

        @Override
        public ServiceReference getServiceReference() {
            return null;
        }
    }

    private class DispatcherMock implements EventAdmin {

        private List<Event> postEvents = new ArrayList<>();
        private List<Event> sendEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            postEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            sendEvents.add(event);
        }

        public List<Event> getPostEvents() {
            return postEvents;
        }

        public List<Event> getSendEvents() {
            return sendEvents;
        }
    }

}
