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
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class EventAdminCollectorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-eventadmin", new RolePrincipal("admin")));

        // just gives time for the factory to populate
        Thread.sleep(1000);

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

        // get event admin to send event
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        Map<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event testEvent = new Event("org/apache/karaf/test", data);
        eventAdmin.sendEvent(testEvent);

        Assert.assertTrue(received.size() >= 1);

        for (Event event : received) {
            if (event.getTopic().equals("decanter/collect/eventadmin/org/apache/karaf/test")) {
                Assert.assertEquals("bar", event.getProperty("foo"));
                Assert.assertEquals("root", event.getProperty("karafName"));
                Assert.assertEquals("eventadmin", event.getProperty("type"));
                return;
            }
        }

        throw new IllegalStateException("No karaf eventadmin event received");
    }

}
