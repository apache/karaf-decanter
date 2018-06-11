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
package org.apache.karaf.decanter.collector.jmx;

import org.junit.*;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.*;
import java.rmi.registry.LocateRegistry;
import java.util.Dictionary;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class TestProtocol {

    private final static Logger LOGGER = LoggerFactory.getLogger(TestProtocol.class);

    private final static String JMX_RMI_SERVICE_URL = "service:jmx:rmi:///jndi/rmi://localhost:8888/decanter";

    private static JMXConnectorServer rmiConnectorServer;
    private static JMXConnectorServer jmxmpConnectorServer;
    private static MBeanServer mBeanServer;

    @BeforeClass
    public static void setup() throws Exception {
        LOGGER.info("Using JMX service URL: {}", JMX_RMI_SERVICE_URL);
        JMXServiceURL serviceURL = new JMXServiceURL(JMX_RMI_SERVICE_URL);

        LOGGER.info("Creating the RMI registry");
        LocateRegistry.createRegistry(8888);

        LOGGER.info("Creating MBeanServer");
        mBeanServer = MBeanServerFactory.createMBeanServer();

        LOGGER.info("Creating RMI connector server");
        rmiConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(serviceURL, null, mBeanServer);
        rmiConnectorServer.start();

        LOGGER.info("Creating JMXMP connector server");
        jmxmpConnectorServer = JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL("jmxmp", null, 9999), null, mBeanServer);
        jmxmpConnectorServer.start();

        ObjectName testObjectName = new ObjectName("decanter.test:type=test");
        StandardMBean testMBean = new StandardMBean(new TestMBeanImpl(), TestMBean.class);
        LOGGER.info("Registering MBeanTest");
        mBeanServer.registerMBean(testMBean, testObjectName);
    }

    @Test
    public void rmiTest() throws Exception {
        JmxCollector collector = new JmxCollector();

        ComponentContextMock componentContextMock = new ComponentContextMock();
        componentContextMock.getProperties().put("type", "jmx-test");
        componentContextMock.getProperties().put("url", "service:jmx:rmi:///jndi/rmi://localhost:8888/decanter");

        DispatcherMock dispatcherMock = new DispatcherMock();
        collector.dispatcher = dispatcherMock;

        collector.activate(componentContextMock);

        collector.run();

        Event event = dispatcherMock.getPostedEvents().get(0);

        Assert.assertEquals("decanter/collect/jmx/jmx-test/decanter/test", event.getTopic());
        Assert.assertEquals("Test", event.getProperty("Test"));
        Assert.assertEquals("decanter.test:type=test", event.getProperty("ObjectName"));
        Assert.assertEquals("service:jmx:rmi:///jndi/rmi://localhost:8888/decanter", event.getProperty("url"));
    }

    @Test
    public void jmxmpTest() throws Exception {
        JmxCollector collector = new JmxCollector();

        ComponentContextMock componentContextMock = new ComponentContextMock();
        componentContextMock.getProperties().put("type", "jmx-test");
        componentContextMock.getProperties().put("url", "service:jmx:jmxmp://localhost:9999");

        DispatcherMock dispatcherMock = new DispatcherMock();
        collector.dispatcher = dispatcherMock;

        collector.activate(componentContextMock);

        collector.run();

        Event event = dispatcherMock.getPostedEvents().get(0);

        Assert.assertEquals("decanter/collect/jmx/jmx-test/decanter/test", event.getTopic());
        Assert.assertEquals("Test", event.getProperty("Test"));
        Assert.assertEquals("decanter.test:type=test", event.getProperty("ObjectName"));
        Assert.assertEquals("service:jmx:jmxmp://localhost:9999", event.getProperty("url"));
    }

    @AfterClass
    public static void teardown() throws Exception {
        rmiConnectorServer.stop();
        jmxmpConnectorServer.stop();
    }

    private class ComponentContextMock implements ComponentContext {

        private Properties properties;

        public ComponentContextMock() {
            this.properties = new Properties();
        }

        @Override
        public Dictionary getProperties() {
            return properties;
        }

        @Override
        public Object locateService(String s) {
            return null;
        }

        @Override
        public <S> S locateService(String s, ServiceReference<S> serviceReference) {
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
        public ServiceReference<?> getServiceReference() {
            return null;
        }

    }

    private class DispatcherMock implements EventAdmin {

        private List<Event> postedEvents = new LinkedList<>();
        private List<Event> sentEvents = new LinkedList<>();

        @Override
        public void postEvent(Event event) {
            postedEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            sentEvents.add(event);
        }

        public List<Event> getPostedEvents() {
            return this.postedEvents;
        }

        public List<Event> getSentEvents() {
            return this.sentEvents;
        }

    }

    public interface TestMBean {

        String getTest() throws MBeanException;
    }

    public static class TestMBeanImpl implements TestMBean {

        @Override
        public String getTest() throws MBeanException {
            return "Test";
        }

    }

}
