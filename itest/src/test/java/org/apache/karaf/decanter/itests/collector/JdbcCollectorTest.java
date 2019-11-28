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
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JdbcCollectorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-derby.cfg", "osgi.jdbc.driver.name", "derby"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-derby.cfg", "osgi.jndi.service.name", "derby"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-derby.cfg", "osgi.jdbc.driver.class", "org.apache.derby.jdbc.EmbeddedDriver"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-derby.cfg", "url", "jdbc:derby:data/derby/test;create=true"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-derby.cfg", "user", "sa"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.datasource-derby.cfg", "password", ""),
                KarafDistributionOption.editConfigurationFilePut("etc/org.apache.karaf.decanter.collector.jdbc.cfg", "dataSource.target", "(osgi.jndi.service.name=derby)"),
                KarafDistributionOption.editConfigurationFilePut("etc/org.apache.karaf.decanter.collector.jdbc.cfg", "query", "select * from TEST")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        // install database
        System.out.println(executeCommand("feature:install jdbc", new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install pax-jdbc-derby", new RolePrincipal("admin")));

        System.out.println(executeCommand("jdbc:ds-list"));

        // get datasource and create table
        DataSource dataSource = getOsgiService(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("create table TEST(detail varchar(50))");
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("insert into TEST values('This is a test')");
            }
        }

        // install decanter
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-collector-jdbc", new RolePrincipal("admin")));

        // gives time for the factory
        Thread.sleep(1000);

        // list scheduler jobs
        System.out.println(executeCommand("scheduler:list"));

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

        // wait scheduler run
        System.out.println("Waiting scheduler run ...");
        while (received.size() == 0) {
            Thread.sleep(500);
        }

        Assert.assertTrue(received.size() >= 1);

        Assert.assertEquals("decanter/collect/jdbc", received.get(0).getTopic());
        Assert.assertEquals("(osgi.jndi.service.name=derby)", received.get(0).getProperty("dataSource.target"));
        Assert.assertEquals("select * from TEST", received.get(0).getProperty("query"));
        Assert.assertEquals("jdbc", received.get(0).getProperty("type"));
        Assert.assertEquals("This is a test", received.get(0).getProperty("DETAIL"));
        Assert.assertEquals(1, received.get(0).getProperty("rowId"));
        Assert.assertEquals(60L, received.get(0).getProperty("scheduler.period"));
        Assert.assertEquals("decanter-collector-jdbc", received.get(0).getProperty("scheduler.name"));
        Assert.assertEquals("root", received.get(0).getProperty("karafName"));
    }

}
