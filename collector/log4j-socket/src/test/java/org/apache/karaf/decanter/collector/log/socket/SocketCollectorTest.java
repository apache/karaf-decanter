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
package org.apache.karaf.decanter.collector.log.socket;

import static org.junit.Assert.assertEquals;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.NOPLogger;
import org.apache.log4j.spi.NOPLoggerRepository;
import org.apache.log4j.spi.ThrowableInformation;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

public class SocketCollectorTest {

    private ComponentContext componentContext;
    private SocketCollector collector;
    private int port;
    private EventAdminStub eventAdmin;

    @Before
    public void setUp() throws IOException {
        port = SocketUtils.findAvailablePort();
        eventAdmin = new EventAdminStub();
        collector = new SocketCollector();
        collector.dispatcher = eventAdmin;
        componentContext = new ComponentContextStub();
        componentContext.getProperties().put(SocketCollector.PORT_NAME, String.valueOf(port));
        componentContext.getProperties().put(SocketCollector.WORKERS_NAME, "1");
    }

    @After
    public void tearDown() throws IOException {
        if (collector != null && collector.isOpen()) {
            collector.close();
        }
    }

    @Test
    public void testLoggerName2Topic() {
        String topic = SocketCollector.loggerName2Topic("decanter/collect/log/", "test.[Tomcat].[localhost].[/]");
        Assert.assertEquals("decanter/collect/log/test/Tomcat/localhost", topic);
    }

    /**
     * Test event handling (1 event)
     */
    @Test
    public void testSocket() throws Exception {
        activate();
        sendEventOnSocket(newLoggingEvent("Sample message"));
        waitUntilEventCountHandled(1);
        assertEquals("Event(s) should have been correctly handled", 1, eventAdmin.getPostEvents().size());
    }

    @Test
    public void testUnknownEvent() throws Exception {
        activate();
        sendEventOnSocket(new UnknownClass());
        waitUntilEventCountHandled(1);
        assertEquals("Event(s) should have been correctly handled", 0, eventAdmin.getPostEvents().size());
    }

    @Test
    public void testDeepObject() throws Exception {
        activate();
        sendEventOnSocket(getMaliciousSerializableDictionaryDemo());
        waitUntilEventCountHandled(1);
        assertEquals(0, eventAdmin.getPostEvents().size());
    }

    public static Object getMaliciousSerializableDictionaryDemo() {
        Dictionary hashtable = new Hashtable();
        Dictionary s1 = hashtable;
        Dictionary s2 = new
                Hashtable();
        for (int i = 0; i < 100; i++) {
            Dictionary t1 = new Hashtable();
            Dictionary t2 = new Hashtable();
            t1.put("afdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdf",
                    "afdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdfafdsgasdgfasdgasdf");
            t2.put("test", "test112312test1123123test1123123test1123123test1123123test1123123test11231233");
            s1.put(t1, t2);
            s1.put(t2, t1);
            s2.put(t2, t1);
            s2.put(t1, t2);
            s1 = t1;
            s2 = t2;
        }
        return (Object) hashtable;
    }

    private static final class UnknownClass implements java.io.Serializable {
        String someValue = "12345";

        public String getValue() {
            return someValue;
        }
    }

    /**
     * Test event handling with multiple clients
     */
    @Test
    public void testSocketMultipleClients() throws Exception {

        activate();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    sendEventOnSocket(newLoggingEvent("Sample message"));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };

        // starts 2 client threads
        Thread thread;
        thread = new Thread(runnable);
        thread.start();
        thread = new Thread(runnable);
        thread.start();

        waitUntilEventCountHandled(2);
        assertEquals("Event(s) should have been correctly handled", 2, eventAdmin.getPostEvents().size());
    }

    /**
     * Test authentication with correct credentials
     */
    @Test
    public void testAuthenticationSuccess() throws Exception {
        componentContext.getProperties().put(SocketCollector.USERNAME, "testuser");
        componentContext.getProperties().put(SocketCollector.PASSWORD, "testpass");
        activate();
        
        sendAuthenticatedEventOnSocket(newLoggingEvent("Authenticated message"), "testuser", "testpass");
        waitUntilEventCountHandled(1);
        assertEquals("Event should have been handled after successful authentication", 1, eventAdmin.getPostEvents().size());
    }

    /**
     * Test authentication with incorrect credentials
     */
    @Test
    public void testAuthenticationFailure() throws Exception {
        componentContext.getProperties().put(SocketCollector.USERNAME, "testuser");
        componentContext.getProperties().put(SocketCollector.PASSWORD, "testpass");
        activate();
        
        try {
            sendAuthenticatedEventOnSocket(newLoggingEvent("Should not be processed"), "testuser", "wrongpass");
            // If we get here, authentication didn't fail as expected
            Assert.fail("Authentication should have failed with wrong password");
        } catch (IOException e) {
            // Expected - authentication failed
            Assert.assertTrue("Exception should indicate authentication failure", 
                e.getMessage() != null && e.getMessage().contains("Authentication failed"));
        }
        
        // Wait a bit to ensure no events were processed
        Thread.sleep(100);
        assertEquals("No events should have been processed after authentication failure", 0, eventAdmin.getPostEvents().size());
    }

    /**
     * Test that authentication is optional (backward compatibility)
     */
    @Test
    public void testNoAuthentication() throws Exception {
        // Don't set username/password
        activate();
        
        // Should work without authentication
        sendEventOnSocket(newLoggingEvent("No auth message"));
        waitUntilEventCountHandled(1);
        assertEquals("Event should have been handled without authentication", 1, eventAdmin.getPostEvents().size());
    }

    /**
     * Test authentication with wrong username
     */
    @Test
    public void testAuthenticationWrongUsername() throws Exception {
        componentContext.getProperties().put(SocketCollector.USERNAME, "testuser");
        componentContext.getProperties().put(SocketCollector.PASSWORD, "testpass");
        activate();
        
        try {
            sendAuthenticatedEventOnSocket(newLoggingEvent("Should not be processed"), "wronguser", "testpass");
            Assert.fail("Authentication should have failed with wrong username");
        } catch (IOException e) {
            // Expected - authentication failed
            Assert.assertTrue("Exception should indicate authentication failure", 
                e.getMessage() != null && e.getMessage().contains("Authentication failed"));
        }
        
        Thread.sleep(100);
        assertEquals("No events should have been processed after authentication failure", 0, eventAdmin.getPostEvents().size());
    }

    private void waitUntilEventCountHandled(int eventCount) throws InterruptedException {
        long timeout = 20000L;
        long start = System.currentTimeMillis();
        boolean hasTimeoutReached = false;
        do {
            hasTimeoutReached = ((System.currentTimeMillis() - start) > timeout);
            Thread.sleep(10L);
        } while (eventAdmin.getPostEvents().size() < eventCount && hasTimeoutReached == false);
    }

    private LoggingEvent newLoggingEvent(String message) {
        return new LoggingEvent(this.getClass().getName(), new NOPLogger(new NOPLoggerRepository(), "NOP"),
                                System.currentTimeMillis(), Level.INFO, message,
                                Thread.currentThread().getName(), new ThrowableInformation((Throwable)null), null, 
                                new LocationInfo(null, null), new Properties());
    }

    /**
     * Launches serverSocket on available port
     * 
     * @throws InterruptedException
     */
    private void activate() throws IOException, InterruptedException {
        collector.activate(componentContext);
        Thread.sleep(200L);
    }

    private void sendEventOnSocket(Object event) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.writeObject(event);
                out.flush();
            }
        }
    }

    private void sendAuthenticatedEventOnSocket(Object event, String username, String password) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            
            // Send authentication
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(usernameBytes.length);
            dos.write(usernameBytes);
            
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(passwordBytes.length);
            dos.write(passwordBytes);
            dos.flush();
            
            // Read authentication response
            int response = socket.getInputStream().read();
            if (response != 1) {
                throw new IOException("Authentication failed, server returned: " + response);
            }
            
            // Send event using ObjectOutputStream (it will write its header)
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.writeObject(event);
                out.flush();
            }
        }
    }

    /**
     * Stub used only for this unit test
     */
    private static class ComponentContextStub implements ComponentContext {

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

    private static class EventAdminStub implements EventAdmin {
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
