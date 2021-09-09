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
package org.apache.karaf.decanter.itests.collector;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.osgi.OsgiClassResolver;
import org.apache.camel.core.osgi.OsgiDataFormatResolver;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.core.osgi.OsgiLanguageResolver;
import org.apache.karaf.decanter.collector.camel.DecanterEventNotifier;
import org.apache.karaf.decanter.collector.camel.DecanterInterceptStrategy;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class CamelCollectorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "camel.version", System.getProperty("camel.version")),
                KarafDistributionOption.features("mvn:org.apache.camel.karaf/apache-camel/" + System.getProperty("camel.version") + "/xml/features", "camel-core")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void testTracer() throws Exception {
        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-camel", new RolePrincipal("admin")));

        // add a event handler
        List<Event> received = new ArrayList();
        EventHandler eventHandler = new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                received.add(event);
            }
        };
        Hashtable serviceProperties = new Hashtable();
        serviceProperties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
        bundleContext.registerService(EventHandler.class, eventHandler, serviceProperties);

        // create route with tracer
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        DecanterInterceptStrategy tracer = new DecanterInterceptStrategy();
        tracer.setDispatcher(eventAdmin);

        RouteBuilder routeBuilder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").routeId("route-test").to("log:foo");
            }
        };
        OsgiDefaultCamelContext camelContext = new OsgiDefaultCamelContext(bundleContext);
        camelContext.setClassResolver(new OsgiClassResolver(camelContext, bundleContext));
        camelContext.setDataFormatResolver(new OsgiDataFormatResolver(bundleContext));
        camelContext.setLanguageResolver(new OsgiLanguageResolver(bundleContext));
        camelContext.setName("context-test");
        camelContext.addInterceptStrategy(tracer);
        camelContext.start();
        camelContext.addRoutes(routeBuilder);

        // send a exchange into the route
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:test", "This is a test", "testHeader", "testValue");

        Assert.assertEquals(1, received.size());

        Assert.assertEquals("decanter/collect/camel/tracer", received.get(0).getTopic());
        Assert.assertEquals("context-test", received.get(0).getProperty("camelContextName"));
        Assert.assertEquals("InOnly", received.get(0).getProperty("exchangePattern"));
        Assert.assertEquals("camelTracer", received.get(0).getProperty("type"));
        Assert.assertEquals("route-test", received.get(0).getProperty("routeId"));
        Assert.assertEquals("direct://test", received.get(0).getProperty("fromEndpointUri"));
        Assert.assertEquals("root", received.get(0).getProperty("karafName"));
        Assert.assertEquals("This is a test", received.get(0).getProperty("inBody"));
        Assert.assertEquals("String", received.get(0).getProperty("inBodyType"));
    }

    @Test
    public void testEventNotifier() throws Exception {
        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-camel", new RolePrincipal("admin")));

        // add a event handler
        List<Event> received = new ArrayList();
        EventHandler eventHandler = new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                received.add(event);
            }
        };
        Hashtable serviceProperties = new Hashtable();
        serviceProperties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
        bundleContext.registerService(EventHandler.class, eventHandler, serviceProperties);

        // create route with notifier
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        DecanterEventNotifier notifier = new DecanterEventNotifier();
        notifier.setDispatcher(eventAdmin);

        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").routeId("route-test").to("log:foo");
            }
        };

        OsgiDefaultCamelContext camelContext = new OsgiDefaultCamelContext(bundleContext);
        camelContext.setClassResolver(new OsgiClassResolver(camelContext, bundleContext));
        camelContext.setDataFormatResolver(new OsgiDataFormatResolver(bundleContext));
        camelContext.setLanguageResolver(new OsgiLanguageResolver(bundleContext));
        camelContext.setName("context-test");
        camelContext.getManagementStrategy().addEventNotifier(notifier);
        camelContext.start();
        camelContext.addRoutes(builder);

        // send a exchange into the route
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBodyAndHeader("direct:test", "This is a test", "testHeader", "testValue");

        Assert.assertTrue(received.size() >= 5);

        // camel context starting
        Assert.assertEquals("decanter/collect/camel/event", received.get(0).getTopic());
        Assert.assertEquals("root", received.get(0).getProperty("karafName"));
        Assert.assertEquals("org.apache.camel.impl.event.CamelContextStartingEvent", received.get(0).getProperty("eventType"));
        Assert.assertEquals("context-test", received.get(0).getProperty("camelContextName"));
        Assert.assertEquals("camelEvent", received.get(0).getProperty("type"));

        // add route
        Assert.assertEquals("decanter/collect/camel/event", received.get(1).getTopic());
        Assert.assertEquals("root", received.get(1).getProperty("karafName"));
        Assert.assertEquals("org.apache.camel.impl.event.CamelContextRoutesStartingEvent", received.get(1).getProperty("eventType"));
        Assert.assertEquals("context-test", received.get(1).getProperty("camelContextName"));
        Assert.assertEquals("camelEvent", received.get(1).getProperty("type"));

        // route started
        Assert.assertEquals("decanter/collect/camel/event", received.get(2).getTopic());
        Assert.assertEquals("root", received.get(2).getProperty("karafName"));
        Assert.assertEquals("org.apache.camel.impl.event.CamelContextRoutesStartedEvent", received.get(2).getProperty("eventType"));
        Assert.assertEquals("context-test", received.get(2).getProperty("camelContextName"));
        Assert.assertEquals("camelEvent", received.get(2).getProperty("type"));

        // camel context started
        Assert.assertEquals("decanter/collect/camel/event", received.get(3).getTopic());
        Assert.assertEquals("root", received.get(3).getProperty("karafName"));
        Assert.assertEquals("org.apache.camel.impl.event.CamelContextStartedEvent", received.get(3).getProperty("eventType"));
        Assert.assertEquals("context-test", received.get(3).getProperty("camelContextName"));
        Assert.assertEquals("camelEvent", received.get(3).getProperty("type"));

        // exchange sending
        Assert.assertEquals("decanter/collect/camel/event", received.get(4).getTopic());
        Assert.assertEquals("root", received.get(4).getProperty("karafName"));
        Assert.assertEquals("org.apache.camel.impl.event.RouteAddedEvent", received.get(4).getProperty("eventType"));
        Assert.assertEquals("context-test", received.get(4).getProperty("camelContextName"));
    }

}
