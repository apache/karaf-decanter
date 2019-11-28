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

import java.util.HashMap;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MongodbAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    /**
     * Require a running MongoDB instance.
     */
    @Test
    public void test() throws Exception {
        System.out.println("Download and start MongoDB with:");
        System.out.println("\tbin/mongod");
        System.out.println("Create a database in MongoDB with:");
        System.out.println("\tbin/mongo");
        System.out.println("\t> use decanter");
        System.out.println("");
        System.out.println("Installing Decanter");
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-common", new RolePrincipal("admin")));
        System.out.println(executeCommand("feature:install decanter-appender-mongodb", new RolePrincipal("admin")));

        System.out.println("Sending Decanter event");
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        System.out.println("Check data in MongoDB with:");
        System.out.println("\tmongo");
        System.out.println("\t> use decanter");
        System.out.println("\t> db.decanter.find()");

        System.out.println("*********************************");
        System.out.println("* Latest Tested Version: 4.2.1  *");
        System.out.println("*********************************");
    }

}
