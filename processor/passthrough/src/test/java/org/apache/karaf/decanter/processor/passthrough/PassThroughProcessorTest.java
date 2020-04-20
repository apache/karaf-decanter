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
package org.apache.karaf.decanter.processor.passthrough;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PassThroughProcessorTest {

    @Test
    public void test() throws Exception {
        MockDispatcher dispatcher = new MockDispatcher();

        PassThroughProcessor processor = new PassThroughProcessor();
        processor.setDispatcher(dispatcher);
        processor.activate("decanter/process/test");

        Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        processor.handleEvent(new Event("decanter/collect/test", data));

        Assert.assertEquals(1, dispatcher.postedEvents.size());

        Assert.assertEquals("passthrough", dispatcher.postedEvents.get(0).getProperty("processor"));
        Assert.assertEquals("bar", dispatcher.postedEvents.get(0).getProperty("foo"));
        Assert.assertEquals("decanter/process/test", dispatcher.postedEvents.get(0).getProperty("event.topics"));
        Assert.assertEquals("decanter/collect/test", dispatcher.postedEvents.get(0).getProperty("source.topics"));
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
