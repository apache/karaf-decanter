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
package org.apache.karaf.decanter.processor.groupby;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.*;

public class GroupByProcessorTest {

    @Test(timeout = 10000)
    public void testFlatten() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();

        GroupByProcessor processor = new GroupByProcessor();
        processor.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("period", "2");
        configuration.put("groupBy", "foo,bar");
        configuration.put("flat", true);
        processor.activate(configuration);

        Map<String, Object> data1 = new HashMap<>();
        data1.put("foo", "foo");
        data1.put("bar", "bar");
        data1.put("first", "first");
        Event event1 = new Event("decanter/collect/first", data1);
        processor.handleEvent(event1);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("foo", "foo");
        data2.put("bar", "bar");
        data2.put("second", "second");
        Event event2 = new Event("decanter/collect/second", data2);
        processor.handleEvent(event2);

        Map<String, Object> data3 = new HashMap<>();
        data3.put("third", "third");
        Event event3 = new Event("decanter/collect/third", data3);
        processor.handleEvent(event3);

        Map<String, Object> data4 = new HashMap<>();
        data4.put("foo", "foo");
        data4.put("bar", "bar");
        data4.put("fourth", "fourth");
        Event event4 = new Event("decanter/collect/fourth", data4);
        processor.handleEvent(event4);

        Map<String, Object> data5 = new HashMap<>();
        data5.put("foo", "other");
        data5.put("bar", "other");
        data5.put("fifth", "fifth");
        Event event5 = new Event("decanter/collect/fifth", data5);
        processor.handleEvent(event5);

        while (dispatcher.postedEvents.size() != 2) {
            Thread.sleep(200);
        }

        Assert.assertEquals(2, dispatcher.postedEvents.size());

        Assert.assertEquals("fifth", dispatcher.postedEvents.get(0).getProperty("fifth"));
        Assert.assertEquals("other", dispatcher.postedEvents.get(0).getProperty("bar"));
        Assert.assertEquals("other", dispatcher.postedEvents.get(0).getProperty("foo"));

        Assert.assertEquals("bar", dispatcher.postedEvents.get(1).getProperty("bar"));
        Assert.assertEquals("fourth", dispatcher.postedEvents.get(1).getProperty("fourth"));
        Assert.assertEquals("first", dispatcher.postedEvents.get(1).getProperty("first"));
        Assert.assertEquals("foo", dispatcher.postedEvents.get(1).getProperty("foo"));
        Assert.assertEquals("second", dispatcher.postedEvents.get(1).getProperty("second"));

        processor.deactivate();
    }

    @Test(timeout = 10000)
    @Ignore("Avoid thread conflict with the other test")
    public void testNotFlatten() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();

        GroupByProcessor processor = new GroupByProcessor();
        processor.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("period", "2");
        configuration.put("groupBy", "foo,bar");
        configuration.put("flat", false);
        processor.activate(configuration);

        Map<String, Object> data1 = new HashMap<>();
        data1.put("foo", "foo");
        data1.put("bar", "bar");
        data1.put("first", "first");
        Event event1 = new Event("decanter/collect/first", data1);
        processor.handleEvent(event1);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("foo", "foo");
        data2.put("bar", "bar");
        data2.put("second", "second");
        Event event2 = new Event("decanter/collect/second", data2);
        processor.handleEvent(event2);

        Map<String, Object> data3 = new HashMap<>();
        data3.put("third", "third");
        Event event3 = new Event("decanter/collect/third", data3);
        processor.handleEvent(event3);

        Map<String, Object> data4 = new HashMap<>();
        data4.put("foo", "foo");
        data4.put("bar", "bar");
        data4.put("fourth", "fourth");
        Event event4 = new Event("decanter/collect/fourth", data4);
        processor.handleEvent(event4);

        Map<String, Object> data5 = new HashMap<>();
        data5.put("foo", "other");
        data5.put("bar", "other");
        data5.put("fifth", "fifth");
        Event event5 = new Event("decanter/collect/fifth", data5);
        processor.handleEvent(event5);

        while (dispatcher.postedEvents.size() != 2) {
            Thread.sleep(200);
        }

        Assert.assertEquals(2, dispatcher.postedEvents.size());

        List<Map<String, Object>> events = (List<Map<String, Object>>) dispatcher.postedEvents.get(0).getProperty("events");
        Assert.assertEquals(1, events.size());

        events = (List<Map<String, Object>>) dispatcher.postedEvents.get(1).getProperty("events");
        Assert.assertEquals(3, events.size());

        processor.deactivate();
    }

    class MockDispatcher implements EventAdmin {

        public List<Event> postedEvents = new ArrayList<>();
        public List<Event> sentEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            postedEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            sentEvents.add(event);
        }
    }

}
