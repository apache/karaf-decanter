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
package org.apache.karaf.decanter.collector.mqtt;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.karaf.decanter.marshaller.json.JsonUnmarshaller;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.net.URI;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class MqttCollectorTest {

    private static BrokerService broker;

    @BeforeClass
    public static void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setUseJmx(false);
        broker.setPersistenceAdapter(new MemoryPersistenceAdapter());
        broker.addConnector(new URI("mqtt://localhost:11883"));
        broker.start();
    }

    @Test
    public void sendDecanterMessage() throws Exception {
        DispatcherMock dispatcherMock = new DispatcherMock();
        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("server.uri", "tcp://localhost:11883");
        componentContext.getProperties().put("client.id", "decanter");
        componentContext.getProperties().put("topic", "decanter");
        JsonUnmarshaller unmarshaller = new JsonUnmarshaller();
        MqttCollector collector = new MqttCollector();
        collector.setDispatcher(dispatcherMock);
        collector.setUnmarshaller(unmarshaller);
        collector.activate(componentContext);

        MqttClient mqttClient = new MqttClient("tcp://localhost:11883", "client");
        mqttClient.connect();
        MqttMessage message = new MqttMessage();
        message.setPayload("{ \"foo\" : \"bar\" }".getBytes());
        mqttClient.publish("decanter", message);
        mqttClient.disconnect();

        Thread.sleep(200L);

        Assert.assertEquals(1, dispatcherMock.getPostEvents().size());
        Event event = dispatcherMock.getPostEvents().get(0);
        Assert.assertEquals("bar", event.getProperty("foo"));
        Assert.assertEquals("mqtt", event.getProperty("type"));
    }

    @AfterClass
    public static void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    private static class ComponentContextMock implements ComponentContext {

        private Dictionary properties = new Hashtable<>();

        @Override
        public Dictionary getProperties() {
            return properties;
        }

        @Override
        public Object locateService(String name) {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public Object locateService(String name, ServiceReference reference) {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public Object[] locateServices(String name) {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public BundleContext getBundleContext() {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public Bundle getUsingBundle() {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public ComponentInstance getComponentInstance() {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public void enableComponent(String name) {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public void disableComponent(String name) {
            throw new NoSuchMethodError("Unimplemented method");
        }

        @Override
        public ServiceReference getServiceReference() {
            throw new NoSuchMethodError("Unimplemented method");
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

        public void reset() {
            postEvents.clear();
            sendEvents.clear();
        }
    }

}
