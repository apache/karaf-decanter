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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class GroupByProcessorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        System.out.println("Installing Decanter Processor GroupBy ...");

        File file = new File(System.getProperty("karaf.etc"), "org.apache.karaf.decanter.processor.groupby.cfg");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("groupBy=foo");
        }

        String configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.processor.groupby)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.processor.groupby)'");
        }

        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version"), new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install decanter-processor-groupby", new RolePrincipal("admin")));

        System.out.println("Adding test event handler ...");
        final List<Event> received = new ArrayList<>();
        EventHandler handler = new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                received.add(event);
            }
        };
        Hashtable<String, String> serviceProperties = new Hashtable<>();
        serviceProperties.put(EventConstants.EVENT_TOPIC, "decanter/process/*");
        bundleContext.registerService(EventHandler.class, handler, serviceProperties);

        System.out.println("Sending test events ...");
        EventAdmin dispatcher = getOsgiService(EventAdmin.class);
        Map<String, Object> data1 = new HashMap<>();
        data1.put("foo", "bar");
        data1.put("first", "first");
        dispatcher.sendEvent(new Event("decanter/collect/test", data1));
        Map<String, Object> data2 = new HashMap<>();
        data2.put("other", "other");
        data2.put("second", "second");
        dispatcher.sendEvent(new Event("decanter/collect/test", data2));
        Map<String, Object> data3 = new HashMap<>();
        data3.put("foo", "bar");
        data3.put("third", "third");
        dispatcher.sendEvent(new Event("decanter/collect/test", data3));

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

    }

}
