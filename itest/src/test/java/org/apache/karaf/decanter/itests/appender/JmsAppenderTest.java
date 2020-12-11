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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JmsAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        String karafVersion = MavenUtils.getArtifactVersion("org.apache.karaf", "apache-karaf");
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "activemq.version", System.getProperty("activemq.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/org.apache.karaf.features.cfg", "featuresRepositories",
                        "mvn:org.apache.karaf.features/framework/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/spring/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/spring-legacy/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/enterprise/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/enterprise-legacy/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/standard/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.activemq/activemq-karaf/" + System.getProperty("activemq.version") + "/xml/features," +
                                "mvn:org.apache.karaf.decanter/apache-karaf-decanter/" + System.getProperty("decanter.version") + "/xml/features"),
                KarafDistributionOption.editConfigurationFileExtend("etc/org.apache.karaf.features.cfg", "featuresBoot", "aries-blueprint,activemq-broker-noweb"),
                KarafDistributionOption.replaceConfigurationFile("etc/activemq.xml", getConfigFile("/etc/activemq.xml"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 60000)
    public void test() throws Exception {
        // install jms
        System.out.println(executeCommand("feature:install jms", new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install pax-jms-activemq", new RolePrincipal("admin")));

        // create connection factory
        System.out.println(executeCommand("jms:create decanter"));

        Thread.sleep(2000);

        System.out.println(executeCommand("jms:connectionfactories"));
        System.out.println(executeCommand("jms:info jms/decanter"));

        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-appender-jms", new RolePrincipal("admin")));

        Thread.sleep(2000);

        // send event
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        Thread.sleep(2000);

        // browse
        String browse = executeCommand("jms:browse jms/decanter decanter");

        System.out.println(browse);

        if (browse.contains("foo=bar")) {
            Assert.assertTrue(browse.contains("foo=bar"));
        } else {
            Assert.assertTrue(browse.contains("\"foo\":\"bar\""));
        }
    }

}
