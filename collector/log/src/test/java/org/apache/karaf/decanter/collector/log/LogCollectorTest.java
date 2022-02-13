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

import org.apache.log4j.Category;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;
import org.ops4j.pax.logging.service.internal.spi.PaxLoggingEventImpl;
import org.ops4j.pax.logging.spi.PaxLevel;
import org.ops4j.pax.logging.spi.PaxLocationInfo;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.*;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

public class LogCollectorTest {

    @Test
    public void testCleanLoggerName() {
        LogCollector collector = new LogCollector();
        
        String loggerName = "wrong$Pattern%For&event!Name";
        String cleanedLoggerName = collector.cleanLoggerName(loggerName);

        assertThat(cleanedLoggerName, not(containsString("%")));
        assertThat(cleanedLoggerName, not(containsString("$")));
        assertThat(cleanedLoggerName, not(containsString("&")));
        assertThat(cleanedLoggerName, not(containsString("!")));
        assertThat(cleanedLoggerName, containsString("_"));
    }

    @Test
    public void testIgnoredLoggerCategories() {
        LogCollector collector = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("ignored.categories", "org.apache.karaf.decanter.collector.log.*,test,other");

        collector.activate(componentContext);

        assertEquals("org.apache.karaf.decanter.collector.log.*", collector.excludedCategories[0]);
        assertEquals("test", collector.excludedCategories[1]);
        assertEquals("other", collector.excludedCategories[2]);

        assertFalse(collector.filterCategory("org.apache.karaf.decanter.other", collector.excludedCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log", collector.excludedCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log.LogEvent", collector.excludedCategories));
    }

    @Test
    public void testExcludedLoggerCategories() {
        LogCollector collector = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("excluded.categories", "org.apache.karaf.decanter.collector.log.*,test,other");

        collector.activate(componentContext);

        assertEquals("org.apache.karaf.decanter.collector.log.*", collector.excludedCategories[0]);
        assertEquals("test", collector.excludedCategories[1]);
        assertEquals("other", collector.excludedCategories[2]);

        assertFalse(collector.filterCategory("org.apache.karaf.decanter.other", collector.excludedCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log", collector.excludedCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log.LogEvent", collector.excludedCategories));
    }

    @Test
    public void testIncludedLoggerCategories() {
        LogCollector collector = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("included.categories", "foo,bar");

        collector.activate(componentContext);

        assertEquals("foo", collector.includedCategories[0]);
        assertEquals("bar", collector.includedCategories[1]);

        assertTrue(collector.filterCategory("foo", collector.includedCategories));
        assertTrue(collector.filterCategory("bar", collector.includedCategories));
        assertFalse(collector.filterCategory("other", collector.includedCategories));
    }

    @Test
    public void testCollectorProcess() throws Exception {
        DispatcherMock dispatcher = new DispatcherMock();

        LogCollector collector = new LogCollector();
        collector.dispatcher = dispatcher;
        collector.activate(new ComponentContextMock());

        collector.appendInternal(new PaxLoggingEventMock("foo", "This is a test"));
        collector.appendInternal(new PaxLoggingEventMock("bar", "Another test"));

        assertEquals(2, dispatcher.postEvents.size());

        assertEquals("foo", dispatcher.postEvents.get(0).getProperty("loggerName"));
        assertEquals("This is a test", dispatcher.postEvents.get(0).getProperty("message"));

        assertEquals("bar", dispatcher.postEvents.get(1).getProperty("loggerName"));
        assertEquals("Another test", dispatcher.postEvents.get(1).getProperty("message"));
    }

    @Test
    public void testExcludedCategoriesCollectorProcess() throws Exception {
        DispatcherMock dispatcher = new DispatcherMock();

        ComponentContextMock componentContext = new ComponentContextMock();
        componentContext.getProperties().put("excluded.categories", "my.excluded.logger");

        LogCollector collector = new LogCollector();
        collector.dispatcher = dispatcher;
        collector.activate(componentContext);

        collector.appendInternal(new PaxLoggingEventMock("foo", "This is a test"));
        collector.appendInternal(new PaxLoggingEventMock("my.excluded.logger", "excluded"));

        assertEquals(1, dispatcher.postEvents.size());

        assertEquals("foo", dispatcher.postEvents.get(0).getProperty("loggerName"));
        assertEquals("This is a test", dispatcher.postEvents.get(0).getProperty("message"));
    }

    @Test
    public void testIncludedCategoriesCollectorProcess() throws Exception {
        DispatcherMock dispatcherMock = new DispatcherMock();

        ComponentContextMock componentContext = new ComponentContextMock();
        componentContext.getProperties().put("included.categories", "my.logger");

        LogCollector collector = new LogCollector();
        collector.dispatcher = dispatcherMock;
        collector.activate(componentContext);

        collector.appendInternal(new PaxLoggingEventMock("my.logger", "This is a test"));
        collector.appendInternal(new PaxLoggingEventMock("other.logger", "another"));
        collector.appendInternal(new PaxLoggingEventMock("bar", "bar"));

        assertEquals(1, dispatcherMock.postEvents.size());

        assertEquals("my.logger", dispatcherMock.postEvents.get(0).getProperty("loggerName"));
        assertEquals("This is a test", dispatcherMock.postEvents.get(0).getProperty("message"));
    }

    @Test
    public void testExcludedIncludedCategoriesCollectorProcess() throws Exception {
        DispatcherMock dispatcherMock = new DispatcherMock();

        ComponentContextMock componentContextMock = new ComponentContextMock();
        componentContextMock.getProperties().put("included.categories", "my.included.logger,my.includedexcluded.logger");
        componentContextMock.getProperties().put("excluded.categories", "my.includedexcluded.logger");

        LogCollector collector = new LogCollector();
        collector.dispatcher = dispatcherMock;
        collector.activate(componentContextMock);

        collector.appendInternal(new PaxLoggingEventMock("foo", "This is foo"));
        collector.appendInternal(new PaxLoggingEventMock("bar", "This is bar"));
        collector.appendInternal(new PaxLoggingEventMock("my.included.logger", "This should be included"));
        collector.appendInternal(new PaxLoggingEventMock("my.includedexcluded.logger", "This should be excluded"));

        assertEquals(1, dispatcherMock.postEvents.size());

        assertEquals("my.included.logger", dispatcherMock.postEvents.get(0).getProperty("loggerName"));
        assertEquals("This should be included", dispatcherMock.postEvents.get(0).getProperty("message"));
    }

    @Test
    public void testDisabledLocationCategories() {
        LogCollector collector = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("location.disabled", "org.apache.karaf.decanter.collector.log.*,test,other");

        collector.activate(componentContext);

        assertEquals("org.apache.karaf.decanter.collector.log.*", collector.locationDisabledCategories[0]);
        assertEquals("test", collector.locationDisabledCategories[1]);
        assertEquals("other", collector.locationDisabledCategories[2]);

        assertFalse(collector.filterCategory("org.apache.karaf.decanter.other", collector.locationDisabledCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log", collector.locationDisabledCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log.LogEvent", collector.locationDisabledCategories));
    }

    @Test
    public void testDisabledLocationCategoriesAllWildcard() {
        LogCollector collector = new LogCollector();

        ComponentContext componentContext = new ComponentContextMock();
        componentContext.getProperties().put("location.disabled", ".*,test,other");

        collector.activate(componentContext);

        assertEquals(".*", collector.locationDisabledCategories[0]);
        assertEquals("test", collector.locationDisabledCategories[1]);
        assertEquals("other", collector.locationDisabledCategories[2]);

        assertTrue(collector.filterCategory("org.apache.karaf.decanter.other", collector.locationDisabledCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log", collector.locationDisabledCategories));
        assertTrue(collector.filterCategory("org.apache.karaf.decanter.collector.log.LogEvent", collector.locationDisabledCategories));
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

    private class PaxLoggingEventMock implements PaxLoggingEvent {

        public String loggerName;
        public String message;

        public PaxLoggingEventMock(String loggerName, String message) {
            this.loggerName = loggerName;
            this.message = message;
        }

        @Override
        public PaxLocationInfo getLocationInformation() {
            return null;
        }

        @Override
        public PaxLevel getLevel() {
            return null;
        }

        @Override
        public String getLoggerName() {
            return this.loggerName;
        }

        @Override
        public String getFQNOfLoggerClass() {
            return null;
        }

        @Override
        public String getMessage() {
            return this.message;
        }

        @Override
        public String getRenderedMessage() {
            return null;
        }

        @Override
        public String getThreadName() {
            return null;
        }

        @Override
        public String[] getThrowableStrRep() {
            return new String[0];
        }

        @Override
        public boolean locationInformationExists() {
            return false;
        }

        @Override
        public long getTimeStamp() {
            return 0;
        }

        @Override
        public Map<String, Object> getProperties() {
            return null;
        }
    }

}
