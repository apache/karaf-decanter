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
package org.apache.karaf.decanter.collector.oshi;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class OshiCollectorTest {

    @Test
    public void test() throws Exception {
        DispatcherMock dispatcherMock = new DispatcherMock();

        OshiCollector collector = new OshiCollector();
        collector.setDispatcher(dispatcherMock);
        collector.activate(new Hashtable<>());

        collector.run();

        Assert.assertEquals(1, dispatcherMock.postedEvents.size());

        Event event = dispatcherMock.postedEvents.get(0);
        for (String property : event.getPropertyNames()) {
            System.out.println(property + ":" + event.getProperty(property));
        }
    }

    class DispatcherMock implements EventAdmin {

        public List<Event> postedEvents = new ArrayList<>();
        public List<Event> sentEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            this.postedEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            this.sentEvents.add(event);
        }
    }

}
