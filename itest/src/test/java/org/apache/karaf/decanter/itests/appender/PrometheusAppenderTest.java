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
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class PrometheusAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 60000)
    public void test() throws Exception {
        System.out.println("Installing Decanter Appender Prometheus ...");
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version"), new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install decanter-appender-prometheus", new RolePrincipal("admin")));
        String configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.prometheus)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.prometheus)'");
        }
        String httpList = executeCommand("http:list");
        while (!httpList.contains("Deployed")) {
            Thread.sleep(500);
            httpList = executeCommand("http:list");
        }

        System.out.println("Sending test event ...");
        EventAdmin dispatcher = getOsgiService(EventAdmin.class);
        HashMap<String, Object> data = new HashMap<>();
        data.put("Test", 0);
        dispatcher.sendEvent(new Event("decanter/collect/test", data));

        boolean found = false;
        StringBuilder builder = new StringBuilder();
        while (!found) {
            URL url = new URL("http://localhost:" + getHttpPort() + "/decanter/prometheus");
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setDoInput(true);
            if (httpURLConnection.getResponseCode() == 200) {
                found = true;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line).append("\n");
                    }
                }
            }
        }

        System.out.println("");
        System.out.println(builder.toString());
        System.out.println("");

        Assert.assertTrue(builder.toString().contains("Test 0.0"));
    }

}
