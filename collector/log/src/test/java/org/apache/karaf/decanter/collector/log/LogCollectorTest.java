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
package org.apache.karaf.decanter.collector.log;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

public class LogCollectorTest {

    @Test
    public void testCleanLoggerName() {
        LogCollector appender = new LogCollector();
        
        String loggerName = "wrong$Pattern%For&event!Name";
        String cleanedLoggerName = appender.cleanLoggerName(loggerName);

        assertThat(cleanedLoggerName, not(containsString("%")));
        assertThat(cleanedLoggerName, not(containsString("$")));
        assertThat(cleanedLoggerName, not(containsString("&")));
        assertThat(cleanedLoggerName, not(containsString("!")));
        assertThat(cleanedLoggerName, containsString("_"));
    }

    @Test
    public void testIgnoredLoggerCategories() {
        LogCollector appender = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("ignored.categories", "org.apache.karaf.decanter.collector.log.*,test,other");

        appender.activate(componentContext);

        assertEquals("org.apache.karaf.decanter.collector.log.*", appender.ignoredCategories[0]);
        assertEquals("test", appender.ignoredCategories[1]);
        assertEquals("other", appender.ignoredCategories[2]);

        assertFalse(appender.isIgnored("org.apache.karaf.decanter.other", appender.ignoredCategories));
        assertTrue(appender.isIgnored("org.apache.karaf.decanter.collector.log", appender.ignoredCategories));
        assertTrue(appender.isIgnored("org.apache.karaf.decanter.collector.log.LogEvent", appender.ignoredCategories));
    }

    @Test
    public void testDisabledLocationCategories() {
        LogCollector appender = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("location.disabled", "org.apache.karaf.decanter.collector.log.*,test,other");

        appender.activate(componentContext);

        assertEquals("org.apache.karaf.decanter.collector.log.*", appender.locationDisabledCategories[0]);
        assertEquals("test", appender.locationDisabledCategories[1]);
        assertEquals("other", appender.locationDisabledCategories[2]);

        assertFalse(appender.isIgnored("org.apache.karaf.decanter.other", appender.locationDisabledCategories));
        assertTrue(appender.isIgnored("org.apache.karaf.decanter.collector.log", appender.locationDisabledCategories));
        assertTrue(appender.isIgnored("org.apache.karaf.decanter.collector.log.LogEvent", appender.locationDisabledCategories));
    }

    @Test
    public void testDisabledLocationCategoriesAllWildcard() {
        LogCollector appender = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("location.disabled", ".*,test,other");

        appender.activate(componentContext);

        assertEquals(".*", appender.locationDisabledCategories[0]);
        assertEquals("test", appender.locationDisabledCategories[1]);
        assertEquals("other", appender.locationDisabledCategories[2]);

        assertTrue(appender.isIgnored("org.apache.karaf.decanter.other", appender.locationDisabledCategories));
        assertTrue(appender.isIgnored("org.apache.karaf.decanter.collector.log", appender.locationDisabledCategories));
        assertTrue(appender.isIgnored("org.apache.karaf.decanter.collector.log.LogEvent", appender.locationDisabledCategories));
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
