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
package org.apache.karaf.decanter.itests.appender;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.osgi.OsgiClassResolver;
import org.apache.camel.core.osgi.OsgiDataFormatResolver;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.core.osgi.OsgiLanguageResolver;
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
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CamelAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "camel.version", System.getProperty("camel.version")),
                KarafDistributionOption.features("mvn:org.apache.camel.karaf/apache-camel/" + System.getProperty("camel.version") + "/xml/features", "camel-core")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 60000)
    public void test() throws Exception {
        final List<Exchange> exchanges = new ArrayList<>();

        // create route
        System.out.println("Creating Camel consumer route ...");
        RouteBuilder routeBuilder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:decanter").routeId("test").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        System.out.println("Adding exchange");
                        exchanges.add(exchange);
                    }
                });
            }
        };

        BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        OsgiDefaultCamelContext camelContext = new OsgiDefaultCamelContext(bundleContext);
        camelContext.setClassResolver(new OsgiClassResolver(camelContext, bundleContext));
        camelContext.setDataFormatResolver(new OsgiDataFormatResolver(bundleContext));
        camelContext.setLanguageResolver(new OsgiLanguageResolver(bundleContext));
        camelContext.setName("context-test");
        bundleContext.registerService(CamelContext.class, camelContext, null);
        camelContext.start();
        camelContext.addRoutes(routeBuilder);

        while (!camelContext.isStarted()) {
            Thread.sleep(200);
        }

        // install decanter
        System.out.println("Installing Decanter Appender Camel ...");
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-appender-camel", new RolePrincipal("admin")));

        String configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.camel)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.camel)'");
        }

        // send event
        System.out.println("Sending event ...");
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        System.out.println("Waiting event ...");
        while (exchanges.size() < 1) {
            Thread.sleep(200);
        }

        Assert.assertEquals(1, exchanges.size());

        HashMap<String, Object> received = exchanges.get(0).getIn().getBody(HashMap.class);

        for (String key : received.keySet()) {
            System.out.println(key + " = " + received.get(key));
        }

        Assert.assertEquals("decanter/collect/test", received.get("event.topics"));
        Assert.assertEquals("bar", received.get("foo"));
    }

}
