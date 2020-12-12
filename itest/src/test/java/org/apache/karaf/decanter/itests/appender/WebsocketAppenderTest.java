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
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.ClientUpgradeResponse;
import org.eclipse.jetty.websocket.client.WebSocketClient;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class WebsocketAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        String karafVersion = MavenUtils.getArtifactVersion("org.apache.karaf", "apache-karaf");
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.features("mvn:org.apache.karaf.features/standard/" + karafVersion + "/xml/features", "http", "jetty")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        // install decanter
        System.out.println("Installing Decanter WebSocket Appender ...");
        System.out.println(executeCommand("feature:repo-add decanter " + System.getProperty("decanter.version")));
        System.out.println(executeCommand("feature:install decanter-appender-websocket-servlet", new RolePrincipal("admin")));

        String configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.websocket.servlet)'");
        while (!configList.contains("service.pid")) {
            Thread.sleep(500);
            configList = executeCommand("config:list '(service.pid=org.apache.karaf.decanter.appender.websocket.servlet)'");
        }

        System.out.println("Waiting websocket servlet deployed ...");
        String httpList = executeCommand("http:list");
        while (!httpList.contains("Deployed")) {
            Thread.sleep(500);
            httpList = executeCommand("http:list");
        }
        System.out.println(httpList);

        // websocket
        System.out.println("Creating testing websocket client ...");
        WebSocketClient client = new WebSocketClient();
        DecanterSocket decanterSocket = new DecanterSocket();
        client.start();
        URI uri = new URI("ws://localhost:" + getHttpPort() + "/decanter-websocket");
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        client.connect(decanterSocket, uri, request).get();

        // sending event
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        decanterSocket.awaitClose(20, TimeUnit.SECONDS);

        System.out.println("Waiting event ...");
        while (decanterSocket.messages.size() != 1) {
            Thread.sleep(200);
        }

        Assert.assertEquals(1, decanterSocket.messages.size());

        Assert.assertTrue(decanterSocket.messages.get(0).contains("\"foo\":\"bar\""));
        Assert.assertTrue(decanterSocket.messages.get(0).contains("\"event_topics\":\"decanter/collect/test\""));

        client.stop();
    }

    @WebSocket
    public class DecanterSocket {

        private final CountDownLatch closeLatch;

        private Session session;

        public final List<String> messages;

        public DecanterSocket() {
            this.messages = new ArrayList<>();
            this.closeLatch = new CountDownLatch(1);
        }

        public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
            return this.closeLatch.await(duration, unit);
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            session.close(StatusCode.NORMAL, "I'm done");
            this.session = null;
            this.closeLatch.countDown();
        }

        @OnWebSocketConnect
        public void onConnect(Session session) {
            this.session = session;
        }

        @OnWebSocketMessage
        public void onMessage(String message) {
            messages.add(message);
        }

    }

}
