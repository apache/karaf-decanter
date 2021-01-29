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
package org.apache.karaf.decanter.collector.camel;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.Map;

public class DecanterEventNotifierTest {

    @Test
    public void testEventNotifier() throws Exception {
        MockEventAdmin eventAdmin = new MockEventAdmin();
        DecanterEventNotifier notifier = new DecanterEventNotifier();
        notifier.setEventAdmin(eventAdmin);

        DefaultCamelContext camelContext = createCamelContext(notifier);

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:start", "TEST", "foo", "bar");

        Assert.assertEquals(12, eventAdmin.getPostEvents().size());

        Event camelContextStartingEvent = eventAdmin.getPostEvents().get(0);
        Assert.assertEquals("test-context", camelContextStartingEvent.getProperty("camelContextName"));
        Assert.assertEquals(org.apache.camel.impl.event.CamelContextStartingEvent.class.getName(), camelContextStartingEvent.getProperty("eventType"));
        Assert.assertEquals("camelEvent", camelContextStartingEvent.getProperty("type"));

        Event routeAddedEvent = eventAdmin.getPostEvents().get(1);
        Assert.assertEquals("test-context", routeAddedEvent.getProperty("camelContextName"));
        Assert.assertEquals(org.apache.camel.impl.event.CamelContextRoutesStartingEvent.class.getName(), routeAddedEvent.getProperty("eventType"));
        Assert.assertEquals("camelEvent", routeAddedEvent.getProperty("type"));

        Event routeStartedEvent = eventAdmin.getPostEvents().get(2);
        Assert.assertEquals("test-context", routeStartedEvent.getProperty("camelContextName"));
        Assert.assertEquals("test-route", routeStartedEvent.getProperty("routeId"));
        Assert.assertEquals(org.apache.camel.impl.event.RouteAddedEvent.class.getName(), routeStartedEvent.getProperty("eventType"));
        Assert.assertEquals("camelEvent", routeStartedEvent.getProperty("type"));

        Event camelContextStartedEvent = eventAdmin.getPostEvents().get(3);
        Assert.assertEquals("test-context", camelContextStartedEvent.getProperty("camelContextName"));
        Assert.assertEquals(org.apache.camel.impl.event.RouteStartedEvent.class.getName(), camelContextStartedEvent.getProperty("eventType"));
        Assert.assertEquals("camelEvent", camelContextStartedEvent.getProperty("type"));

        Event exchangeSendingEvent = eventAdmin.getPostEvents().get(4);
        Assert.assertEquals("test-context", exchangeSendingEvent.getProperty("camelContextName"));
        Assert.assertEquals(org.apache.camel.impl.event.CamelContextRoutesStartedEvent.class.getName(), exchangeSendingEvent.getProperty("eventType"));
        Assert.assertEquals("camelEvent", exchangeSendingEvent.getProperty("type"));

        Event exchangeCreatedEvent = eventAdmin.getPostEvents().get(5);
        Assert.assertEquals("test-context", exchangeCreatedEvent.getProperty("camelContextName"));
        Assert.assertEquals(org.apache.camel.impl.event.CamelContextStartedEvent.class.getName(), exchangeCreatedEvent.getProperty("eventType"));
        Assert.assertEquals("camelEvent", exchangeCreatedEvent.getProperty("type"));
    }

    @Test
    public void testCamelContextFilter() throws Exception {
        MockEventAdmin eventAdmin = new MockEventAdmin();
        DecanterEventNotifier notifier = new DecanterEventNotifier();
        notifier.setEventAdmin(eventAdmin);
        notifier.setCamelContextMatcher("foo");

        DefaultCamelContext camelContext = createCamelContext(notifier);

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:start", "TEST", "foo", "bar");

        Assert.assertEquals(0, eventAdmin.getPostEvents().size());
    }

    @Test
    public void testRouteIdFilter() throws Exception {
        MockEventAdmin eventAdmin = new MockEventAdmin();
        DecanterEventNotifier notifier = new DecanterEventNotifier();
        notifier.setEventAdmin(eventAdmin);
        notifier.setCamelContextMatcher(".*");
        notifier.setRouteMatcher("foo");

        DefaultCamelContext camelContext = createCamelContext(notifier);

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:start", "TEST", "foo", "bar");

        Assert.assertEquals(6, eventAdmin.getPostEvents().size());
    }

    @Test
    public void testIgnoredEvents() throws Exception {
        MockEventAdmin eventAdmin = new MockEventAdmin();
        DecanterEventNotifier notifier = new DecanterEventNotifier();
        notifier.setEventAdmin(eventAdmin);
        notifier.setIgnoreCamelContextEvents(true);

        DefaultCamelContext camelContext = createCamelContext(notifier);

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:start", "TEST", "foo", "bar");

        Assert.assertEquals(8, eventAdmin.getPostEvents().size());
    }

    @Test
    public void testExtender() throws Exception {
        MockEventAdmin eventAdmin = new MockEventAdmin();
        DecanterEventNotifier notifier = new DecanterEventNotifier();
        notifier.setIgnoreCamelContextEvents(true);
        notifier.setIgnoreRouteEvents(true);
        notifier.setEventAdmin(eventAdmin);
        notifier.setExtender(new TestExtender());

        DefaultCamelContext camelContext = createCamelContext(notifier);

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:start", "TEST", "foo", "bar");

        Assert.assertEquals(6, eventAdmin.getPostEvents().size());

        Assert.assertEquals("test", eventAdmin.getPostEvents().get(0).getProperty("extender-test"));
    }

    private DefaultCamelContext createCamelContext(DecanterEventNotifier notifier) throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("test-route").to("log:foo");
            }
        };
    
        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.setName("test-context");
        camelContext.addRoutes(builder);
        camelContext.getManagementStrategy().addEventNotifier(notifier);
        camelContext.start();
        return camelContext;
    }

    private class TestExtender implements DecanterCamelEventExtender {

        @Override
        public void extend(Map<String, Object> decanterData, Exchange camelExchange) {
            decanterData.put("extender-test", "test");
        }

    }

}
