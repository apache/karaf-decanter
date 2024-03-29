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
import org.osgi.framework.Bundle;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.http.HttpService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RestAppenderTest extends KarafTestSupport {

    @Inject
    private HttpService httpService;

    @Configuration
    public Option[] config() {
        String karafVersion = MavenUtils.getArtifactVersion("org.apache.karaf", "apache-karaf");
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.features("mvn:org.apache.karaf.features/standard/" + karafVersion + "/xml/features", "http", "pax-web-karaf")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 60000)
    public void test() throws Exception {
        List<String> calls = new ArrayList<>();
        // register servlet
        httpService.registerServlet("/test", new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                try (BufferedReader reader = new BufferedReader(req.getReader())) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        calls.add(line);
                    }
                }
                resp.setStatus(200);
                try (BufferedWriter writer = new BufferedWriter(resp.getWriter())) {
                    writer.write("DONE");
                }
            }
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                // nothing to do
            }
        }, null, null);

        System.out.println("Waiting testing REST service ...");
        String httpList = executeCommand("web:servlet-list");
        while (!httpList.contains("RestAppenderTest")) {
            Thread.sleep(500);
            httpList = executeCommand("web:servlet-list");
        }
        System.out.println(httpList);

        // configure appender
        File file = new File(System.getProperty("karaf.etc"), "org.apache.karaf.decanter.appender.rest.cfg");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("uri=http://localhost:" + getHttpPort() + "/test\n");
            writer.write("marshaller.target=(dataFormat=json)");
        }

        System.out.println("Waiting org.apache.karaf.decanter.appender.rest configuration ...");
        String configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.rest)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.rest)'");
        }
        System.out.println(configList);

        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-appender-rest", new RolePrincipal("admin")));

        Thread.sleep(2000);

        // send event
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        while (calls.size() != 1) {
            Thread.sleep(200);
        }

        Assert.assertEquals(1, calls.size());

        Assert.assertTrue(calls.get(0).contains("\"foo\":\"bar\""));
        Assert.assertTrue(calls.get(0).contains("\"event_topics\":\"decanter/collect/test\""));
    }

}
