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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class LogCollectorTest extends KarafTestSupport {

    private final static Logger LOGGER = LoggerFactory.getLogger(LogCollectorTest.class);

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 60000)
    public void test() throws Exception {
        // install log collector
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-log", new RolePrincipal("admin")));

        List<Event> received = new ArrayList<>();
        // plugin event handler
        EventHandler eventHandler = new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                received.add(event);
            }
        };
        Hashtable serviceProperties = new Hashtable();
        serviceProperties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
        bundleContext.registerService(EventHandler.class, eventHandler, serviceProperties);

        LOGGER.info("This is a test");

        while (received.size() < 1) {
            Thread.sleep(200);
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
        Assert.assertEquals("decanter/collect/log/org_apache_karaf_decanter_itests_collector_LogCollectorTest", received.get(0).getTopic());
        Assert.assertEquals("INFO", received.get(0).getProperty("level"));
        Assert.assertEquals("log", received.get(0).getProperty("type"));
        Assert.assertEquals("This is a test", received.get(0).getProperty("message"));
        Assert.assertEquals("root", received.get(0).getProperty("karafName"));
        Assert.assertEquals("org.apache.karaf.decanter.itests.collector.LogCollectorTest", received.get(0).getProperty("loggerName"));
        Assert.assertEquals("org.ops4j.pax.logging.slf4j.Slf4jLogger", received.get(0).getProperty("loggerClass"));
        Assert.assertEquals("This is a test", received.get(0).getProperty("renderedMessage"));

    }

}
