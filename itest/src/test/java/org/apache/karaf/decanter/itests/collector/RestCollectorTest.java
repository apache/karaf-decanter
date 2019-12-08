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
import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RestCollectorTest extends KarafTestSupport {

    @Inject
    private HttpService httpService;

    @Configuration
    public Option[] config() {
        String karafVersion = MavenUtils.getArtifactVersion("org.apache.karaf", "apache-karaf");
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.features("mvn:org.apache.karaf.features/standard/" + karafVersion + "/xml/features", "http")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        // register servlet
        httpService.registerServlet("/test", new HttpServlet() {
            @Override
            public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()))) {
                    writer.write("{\"test\":\"test\"}");
                    writer.flush();
                }
            }
        }, null, null);

        String httpList = executeCommand("http:list");
        while (!httpList.contains("Deployed")) {
            Thread.sleep(200);
            httpList = executeCommand("http:list");
        }
        Assert.assertTrue(httpList.contains("/test"));

        // create event handler
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

        // configure collector
        File file = new File(System.getProperty("karaf.etc"), "org.apache.karaf.decanter.collector.rest-1.cfg");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("url=http://localhost:" + getHttpPort() + "\n");
            writer.write("paths=test\n");
            writer.write("unmarshaller.target=(dataFormat=json)");
        }

        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-rest", new RolePrincipal("admin")));

        System.out.println("Waiting scheduler ...");
        while (received.size() == 0) {
            Thread.sleep(500);
        }

        Assert.assertEquals(1, received.size());

        Assert.assertEquals("decanter/collect/rest", received.get(0).getTopic());
        Assert.assertEquals("test", received.get(0).getProperty("test"));
        Assert.assertEquals("OK", received.get(0).getProperty("http.response.message"));
        Assert.assertEquals("rest", received.get(0).getProperty("type"));
        Assert.assertEquals(200, received.get(0).getProperty("http.response.code"));
    }

}
