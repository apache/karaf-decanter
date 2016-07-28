/*
 * Licen	sed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.karaf.decanter.appender.rest;

import java.net.URISyntaxException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.service.event.Event;

public class RestAppenderTest {
    
    private static final int NUM_MESSAGES = 100000;

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyURI() throws URISyntaxException {
        RestAppender appender = new RestAppender();
        Dictionary<String, Object> config = new Hashtable<>();
        appender.activate(config);
    }
    
    @Ignore
    @Test
    public void testSend() throws URISyntaxException, InterruptedException {
        RestAppender appender = createAppender();
        sendMessage(appender);
    }

    @Ignore
    @Test
    public void testPerformance() throws URISyntaxException, InterruptedException {
        RestAppender appender = createAppender();
        sendMessages(appender);
        long start = System.currentTimeMillis();
        sendMessages(appender);
        long end = System.currentTimeMillis();
        System.out.println(NUM_MESSAGES * 1000 / (end-start));
    }

    private RestAppender createAppender() throws URISyntaxException {
        RestAppender appender = new RestAppender();
        Marshaller marshaller = new JsonMarshaller();
        appender.setMarshaller(marshaller);
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("uri", "http://localhost:8181/decanter/collect");
        appender.activate(config);
        return appender;
    }

    private void sendMessages(final RestAppender appender) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        for (int c=0; c<NUM_MESSAGES; c++) {
            executor.submit(new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    sendMessage(appender);
                    return null;
                }
            });
            
        }
        executor.shutdown();
        executor.awaitTermination(100, TimeUnit.SECONDS);
    }

    private void sendMessage(RestAppender appender) {
        Map<String, Object> props = new HashMap<>();
        props.put("key1", "value1");
        Event event = new Event("decanter/collect", props);
        appender.handleEvent(event);
    }
}
