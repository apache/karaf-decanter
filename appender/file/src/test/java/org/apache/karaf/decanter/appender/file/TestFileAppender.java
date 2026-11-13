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
package org.apache.karaf.decanter.appender.file;

import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.apache.karaf.decanter.marshaller.csv.CsvMarshaller;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.osgi.service.event.Event;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;

public class TestFileAppender {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void test() throws Exception {
        File file = temporaryFolder.newFile("decanter");

        FileAppender fileAppender = new FileAppender();
        fileAppender.marshaller = new CsvMarshaller();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(FileAppender.FILENAME_PROPERTY, file.getAbsolutePath());
        fileAppender.open(config);

        Map<String, String> data = new HashMap<>();
        data.put("a", "b");
        data.put("c", "d");
        fileAppender.handleEvent(new Event("testTopic", data));
        fileAppender.handleEvent(new Event("testTopic", data));
        fileAppender.handleEvent(new Event("testTopic", data));
        fileAppender.deactivate();

        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                Assert.assertEquals("a=b,c=d,event.topics=testTopic", line);
            }
        }
        Assert.assertEquals(3, lineCount);
    }

    @Test
    public void testWithFilter() throws Exception {
        File file = temporaryFolder.newFile("filtered");

        FileAppender fileAppender = new FileAppender();
        fileAppender.marshaller = new CsvMarshaller();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(FileAppender.FILENAME_PROPERTY, file.getAbsolutePath());
        config.put(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG, ".*refused.*");
        config.put(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG, ".*refused.*");
        fileAppender.open(config);

        Map<String, String> data = new HashMap<>();
        data.put("refused_property", "test");
        fileAppender.handleEvent(new Event("testTopic", data));

        data = new HashMap<>();
        data.put("property", "refused_value");
        fileAppender.handleEvent(new Event("testTopic", data));

        data = new HashMap<>();
        data.put("a", "b");
        fileAppender.handleEvent(new Event("testTopic", data));
        fileAppender.deactivate();

        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                Assert.assertEquals("a=b,event.topics=testTopic", line);
            }
        }
        Assert.assertEquals(1, lineCount);
    }

    @Test
    public void testConcurrentEvents() throws Exception {
        File file = temporaryFolder.newFile("concurrent");

        FileAppender fileAppender = new FileAppender();
        fileAppender.marshaller = new CsvMarshaller();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put(FileAppender.FILENAME_PROPERTY, file.getAbsolutePath());
        fileAppender.open(config);

        int threads = 40; // decanter-common configures EventAdmin with ThreadPoolSize=40
        int eventsPerThread = 500;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            futures.add(executor.submit(() -> {
                start.await();
                for (int seq = 0; seq < eventsPerThread; seq++) {
                    Map<String, String> data = new HashMap<>();
                    data.put("thread", String.valueOf(thread));
                    data.put("seq", String.valueOf(seq));
                    fileAppender.handleEvent(new Event("testTopic", data));
                }
                return null;
            }));
        }
        start.countDown();
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        // ensure that all threads finished without throwing an exception
        for (Future<?> future : futures) {
            future.get();
        }
        fileAppender.deactivate();

        int lineCount = 0;
        int[] expectedSeq = new int[threads];
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                // each line must hold exactly one event, e.g. "seq=0,thread=0,event.topics=testTopic",
                // two events on the same line, or a blank line, means that writes from different threads interleaved
                String[] parts = line.split(",");
                Assert.assertEquals(3, parts.length);
                Map<String, String> fields = new HashMap<>();
                for (String field : parts) {
                    String[] keyValue = field.split("=");
                    Assert.assertEquals(2, keyValue.length);
                    fields.put(keyValue[0], keyValue[1]);
                }
                Assert.assertEquals("testTopic", fields.get("event.topics"));
                // events sent by the same thread keep their order
                int thread = Integer.parseInt(fields.get("thread"));
                Assert.assertEquals(expectedSeq[thread], Integer.parseInt(fields.get("seq")));
                expectedSeq[thread]++;
            }
        }
        Assert.assertEquals(threads * eventsPerThread, lineCount);
    }

}
