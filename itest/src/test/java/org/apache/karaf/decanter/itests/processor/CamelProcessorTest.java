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
package org.apache.karaf.decanter.itests.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
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
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.*;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CamelProcessorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.features("mvn:org.apache.camel.karaf/apache-camel/" + System.getProperty("camel.version") + "/xml/features", "camel-core")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        System.out.println("Installing Decanter Processor Camel ...");
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version"), new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install decanter-processor-camel", new RolePrincipal("admin")));
        String configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.processor.camel)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.processor.camel)'");
        }

        System.out.println("Creating test Camel route ...");
        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.setName("decanter-test-context");
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:decanter-delegate")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Map<String, Object> body = exchange.getIn().getBody(Map.class);
                                body.put("camel-processing", "of-course");
                                exchange.getIn().setBody(body, Map.class);
                            }
                        }).to("direct-vm:decanter-callback");
            }
        });
        camelContext.start();
        while (!camelContext.isStarted()) {
            Thread.sleep(200);
        }

        System.out.println("Adding event handler ...");
        final List<Event> received = new ArrayList<>();
        EventHandler eventHandler = new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                received.add(event);
            }
        };
        Hashtable<String, Object> serviceProperties = new Hashtable<>();
        serviceProperties.put(EventConstants.EVENT_TOPIC, "decanter/process/*");
        bundleContext.registerService(EventHandler.class, eventHandler, serviceProperties);

        System.out.println("Sending test events ...");
        EventAdmin dispatcher = getOsgiService(EventAdmin.class);
        Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        dispatcher.sendEvent(new Event("decanter/collect/test", data));

        System.out.println("Waiting events ...");
        while (received.size() < 1) {
            Thread.sleep(500);
        }

        System.out.println("");

        for (int i = 0; i < received.size(); i++) {
            for (String property : received.get(i).getPropertyNames()) {
                System.out.println(property + " = " + received.get(i).getProperty(property));
            }
            System.out.println("========");
        }

        System.out.println("");

        Assert.assertEquals(1, received.size());

        Assert.assertEquals("of-course", received.get(0).getProperty("camel-processing"));
        Assert.assertEquals("camel", received.get(0).getProperty("processor"));
        Assert.assertEquals("bar", received.get(0).getProperty("foo"));
    }

}
