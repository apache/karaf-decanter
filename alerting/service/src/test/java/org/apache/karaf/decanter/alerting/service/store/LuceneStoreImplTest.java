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
package org.apache.karaf.decanter.alerting.service.store;

import org.apache.karaf.decanter.alerting.service.Alert;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.junit.*;
import org.osgi.service.event.Event;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LuceneStoreImplTest {

    static LuceneStoreImpl alertingService;

    @BeforeClass
    static public void setup() throws Exception {
        System.setProperty("karaf.data", "target/alerting/store");
        alertingService = new LuceneStoreImpl();
        alertingService.activate();
    }

    @AfterClass
    static public void teardown() throws Exception {
        alertingService.deactivate();
    }

    @After
    public void cleanup() throws Exception {
        alertingService.cleanup();
    }

    @Test
    public void testStore() throws Exception {
        HashMap<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        data.put("myint", 28);
        data.put("myfloat", 2.5);
        data.put("mylong", 100L);
        data.put("mypojo", new MyPojo("pojo"));
        Event event = new Event("topic", data);
        alertingService.store(event);

        List<Alert> alerts = alertingService.list();
        Assert.assertEquals(1, alerts.size());

        Alert alert = alerts.get(0);
        Assert.assertEquals("bar", alert.get("foo"));
        Assert.assertEquals(28, alert.get("myint"));
        Assert.assertEquals(2.5, alert.get("myfloat"));
        Assert.assertEquals(100L, alert.get("mylong"));
        Assert.assertEquals("pojo", alert.get("mypojo"));
        Assert.assertEquals("topic", alert.get("event.topics"));
        Assert.assertNotNull(alert.get("alertTimestamp"));
    }

    @Test
    public void testQuery() throws Exception {
        HashMap<String, Object> data = new HashMap<>();
        data.put("threadCount", 200);
        data.put("alertTimestamp", System.currentTimeMillis());
        data.put("mydouble", 2.5);
        data.put("content", "this is a raw text");
        Event event = new Event("collected", data);
        alertingService.store(event);

        data = new HashMap<>();
        data.put("other", "value");
        data.put("alertTimestamp", System.currentTimeMillis());
        event = new Event("collected", data);
        alertingService.store(event);

        List<Alert> found = alertingService.query("content:this* AND threadCount:[0 TO 200]");

        Assert.assertEquals(1, found.size());
        Assert.assertEquals(200, found.get(0).get("threadCount"));
        Assert.assertEquals("this is a raw text", found.get(0).get("content"));
        Assert.assertEquals(2.5, found.get(0).get("mydouble"));

        long past = System.currentTimeMillis() - 60 * 1000;

        data = new HashMap<>();
        data.put("scope", "out");
        data.put("alertTimestamp", System.currentTimeMillis() - 2 * 60 * 60 * 1000);
        event = new Event("collected", data);
        alertingService.store(event);

        found = alertingService.query("alertTimestamp:[" + past + " TO *]");

        Assert.assertEquals(2, found.size());

        data = new HashMap<>();
        data.put("period", "test");
        data.put("alertTimestamp", System.currentTimeMillis() - 5 * 60 * 1000);
        event = new Event("collected", data);
        alertingService.store(event);

        found = alertingService.query("period:test");
        Assert.assertEquals(1, found.size());
    }

    @Test
    public void testEviction() throws Exception {
        HashMap<String, Object> data = new HashMap<>();
        data.put("first", 1);
        Event event = new Event("collected", data);
        alertingService.store(event);

        data = new HashMap<>();
        data.put("second", 2);
        data.put("alertRule", "test");
        event = new Event("collected", data);
        alertingService.store(event);

        Assert.assertEquals(2, alertingService.list().size());

        alertingService.eviction();

        Assert.assertEquals(1, alertingService.list().size());
    }

    @Test
    public void testPointsStore() throws Exception {
        Map<String, PointsConfig> empty = LuceneStoreImpl.loadPoints();
        Assert.assertEquals(0, empty.size());

        Map<String, PointsConfig> points = new HashMap<>();
        points.put("double", new PointsConfig(NumberFormat.getInstance(), Double.class));
        points.put("float", new PointsConfig(NumberFormat.getInstance(), Float.class));
        points.put("integer", new PointsConfig(NumberFormat.getInstance(), Integer.class));
        points.put("long", new PointsConfig(NumberFormat.getInstance(), Long.class));
        LuceneStoreImpl.savePoints(points);

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(Paths.get(System.getProperty("karaf.data"), LuceneStoreImpl.POINTS_DIRECTORY).toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        }
        String pointsString = builder.toString();
        Assert.assertTrue(pointsString.contains("integer=integer"));
        Assert.assertTrue(pointsString.contains("double=double"));
        Assert.assertTrue(pointsString.contains("long=long"));
        Assert.assertTrue(pointsString.contains("float=float"));

        Map<String, PointsConfig> newPoints = LuceneStoreImpl.loadPoints();

        Assert.assertEquals(4, newPoints.size());
        Assert.assertEquals("java.lang.Integer", newPoints.get("integer").getType().getName());
        Assert.assertEquals("java.lang.Double", newPoints.get("double").getType().getName());
        Assert.assertEquals("java.lang.Long", newPoints.get("long").getType().getName());
        Assert.assertEquals("java.lang.Float", newPoints.get("float").getType().getName());
    }

    private class MyPojo {

        private String name;

        public MyPojo(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

    }

}
