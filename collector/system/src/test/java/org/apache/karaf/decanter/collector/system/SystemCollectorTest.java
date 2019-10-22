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
package org.apache.karaf.decanter.collector.system;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

public class SystemCollectorTest {

    private ComponentContext componentContext;
    private EventAdminStub eventAdmin;

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfiguration() {
        SystemCollector collector = new SystemCollector();
        this.eventAdmin = new EventAdminStub();
        collector.dispatcher = eventAdmin;
        componentContext = new ComponentContextStub();
        componentContext.getProperties().put("thread.number", "A");
        componentContext.getProperties().put("command.df1", "df -h");
        collector.activate(componentContext);
    }

    @Test
    public void testWithThreads() throws Exception {
        SystemCollector collector = new SystemCollector();
        this.eventAdmin = new EventAdminStub();
        collector.dispatcher = eventAdmin;
        componentContext = new ComponentContextStub();
        componentContext.getProperties().put("thread.number", 5);
        componentContext.getProperties().put("command.df1", "df -h");
        componentContext.getProperties().put("command.df2", "df -h");
        componentContext.getProperties().put("command.df3", "df -h");
        componentContext.getProperties().put("command.df4", "df -h");
        componentContext.getProperties().put("command.df5", "df -h");
        collector.activate(componentContext);
        collector.run();
        waitUntilEventCountHandled(5);
        Assert.assertEquals(5, eventAdmin.getPostEvents().size());
    }

    @Test
    public void testBulkWithThreads() throws Exception {
        SystemCollector collector = new SystemCollector();
        this.eventAdmin = new EventAdminStub();
        collector.dispatcher = eventAdmin;
        componentContext = new ComponentContextStub();
        componentContext.getProperties().put("thread.number", 5);
        for (int cpt = 0; cpt < 1000; cpt++) {
            componentContext.getProperties().put("command.df" + cpt, "df -h");
        }
        collector.activate(componentContext);
        collector.run();
        waitUntilEventCountHandled(1000);
        Assert.assertEquals(1000, eventAdmin.getPostEvents().size());
    }

    @After
    public void tearDown() {
        this.eventAdmin.reset();
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
