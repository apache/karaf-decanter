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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JdbcAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-decanter.cfg", "osgi.jdbc.driver.name", "derby"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-decanter.cfg", "osgi.jndi.service.name", "jdbc/decanter"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-decanter.cfg", "osgi.jdbc.driver.class", "org.apache.derby.jdbc.EmbeddedDriver"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-decanter.cfg", "url", "jdbc:derby:data/decanter/test;create=true"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-decanter.cfg", "user", "sa"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-decanter.cfg", "password", "")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        // install database
        System.out.println(executeCommand("feature:install jdbc", new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install pax-jdbc-derby", new RolePrincipal("admin")));

        System.out.println(executeCommand("jdbc:ds-list"));

        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-appender-jdbc", new RolePrincipal("admin")));

        // send event
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        // check database content
        DataSource dataSource = getOsgiService(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("select * from decanter")) {
                    resultSet.next();
                    String json = resultSet.getString(2);
                    Assert.assertTrue(json.contains("\"foo\":\"bar\""));
                    Assert.assertTrue(json.contains("\"event_topics\":\"decanter/collect/test\""));
                }
            }
        }
    }

}
