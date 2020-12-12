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
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FileCollectorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/org.apache.karaf.decanter.collector.file-test.cfg", "type", "file-test"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.apache.karaf.decanter.collector.file-test.cfg", "path", "test.log"),
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 30000)
    public void test() throws Exception {
        // install decanter
        System.out.println("Installing Decanter Collector File ...");
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-file", new RolePrincipal("admin")));

        String configList = executeCommand("config:list '(service.factoryPid=org.apache.karaf.decanter.collector.file)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.factoryPid=org.apache.karaf.decanter.collector.file)'");
        }

        // add a event handler
        System.out.println("Adding test event handler ...");
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

        // append data in the file
        System.out.println("Writing data in test.log file ...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("test.log")))) {
            writer.write("This is a test\n");
            writer.write("Another test\n");
            writer.flush();
        }

        System.out.println("Waiting events ...");
        while (received.size() < 2) {
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

        Assert.assertEquals(2, received.size());

        // first line
        Assert.assertEquals("decanter/collect/file/file-test", received.get(0).getTopic());
        Assert.assertEquals("file-test", received.get(0).getProperty("type"));
        Assert.assertEquals("test.log", received.get(0).getProperty("path"));
        Assert.assertEquals("This is a test", received.get(0).getProperty("line_file-test"));

        // second line
        Assert.assertEquals("decanter/collect/file/file-test", received.get(1).getTopic());
        Assert.assertEquals("file-test", received.get(1).getProperty("type"));
        Assert.assertEquals("test.log", received.get(1).getProperty("path"));
        Assert.assertEquals("Another test", received.get(1).getProperty("line_file-test"));
    }

}
