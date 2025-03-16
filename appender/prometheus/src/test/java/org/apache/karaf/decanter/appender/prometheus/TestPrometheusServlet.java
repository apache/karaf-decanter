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
package org.apache.karaf.decanter.appender.prometheus;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.HashMap;
import java.util.Map;

public class TestPrometheusServlet {

    @Test
    public void testDefaultGaugeName() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("Test", 0);
        Event event = new Event("TestTopic", data);

        String gaugeName = PrometheusServlet.createUniqueGaugeName(event, null, "Test");

        System.out.println(gaugeName);

        Assert.assertTrue(gaugeName.matches("TestTopic_(.*)_Test"));
    }

    @Test
    public void testGaugeNameWithObjectName() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("Test", 0);
        data.put("ObjectName", "test:MyBean,name=foo,other=bar");
        Event event = new Event("TestTopic", data);

        String gaugeName = PrometheusServlet.createUniqueGaugeName(event, null, "Test");

        System.out.println(gaugeName);

        Assert.assertEquals("TestTopic_test_MyBean_name_foo_other_bar_Test", gaugeName);
    }

}
