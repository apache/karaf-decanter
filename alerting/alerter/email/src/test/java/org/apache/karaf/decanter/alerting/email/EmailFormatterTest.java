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
package org.apache.karaf.decanter.alerting.email;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.HashMap;
import java.util.Map;

public class EmailFormatterTest {

    EmailFormatter formatter = new EmailFormatter();

    private Event createEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("alertLevel", "SEVERE");
        data.put("alertAttribute", "TestAttribute");
        data.put("alertPattern", "TestPattern");
        data.put("foo", "bar");
        data.put("other", "test");
        return new Event("foo", data);
    }

    @Test
    public void testSubjectDefaultTemplate() {
        String subject = formatter.formatSubject(null, createEvent());
        Assert.assertEquals("[SEVERE] Alert TestPattern", subject.trim());

        // test recovery path
        Map<String, Object> data = new HashMap<>();
        data.put("alertLevel", "CRITICAL");
        data.put("alertAttribute", "recovery");
        data.put("alertBackToNormal", true);
        data.put("alertPattern", "message:*");
        Event event = new Event("foo", data);
        subject = formatter.formatSubject(null, event);
        Assert.assertEquals("Alert on message:* is back to normal", subject.trim());
    }

    @Test
    public void testSubjectExternalTemplate() {
        String subject = formatter.formatSubject(this.getClass().getClassLoader().getResource("subjectTemplate.vm").getPath(), createEvent());
        Assert.assertEquals("Simple TestAttribute for SEVERE", subject);
    }

    @Test
    public void testSubjectEventDefinedTemplate() {
        Map<String, Object> data = new HashMap<>();
        data.put("subject.template.location", this.getClass().getClassLoader().getResource("propertySubjectTemplate.vm").getPath());
        data.put("alertLevel", "SEVERE");
        data.put("alertAttribute", "TestAttribute");
        Event event = new Event("foo", data);
        String subject = formatter.formatSubject(null, event);
        Assert.assertEquals("Property defined TestAttribute with SEVERE", subject);
    }

    @Test
    public void testBodyDefaultTemplate() {
        String body = formatter.formatBody(null, createEvent());
        System.out.println(body);
        Assert.assertTrue(body.contains("SEVERE alert: condition TestPattern alert"));
        Assert.assertTrue(body.contains("other : test"));
        Assert.assertTrue(body.contains("alertPattern : TestPattern"));
        Assert.assertTrue(body.contains("event.topics : foo"));
        Assert.assertTrue(body.contains("foo : bar"));
        Assert.assertTrue(body.contains("alertLevel : SEVERE"));
    }

    @Test
    public void testBodyExternalTemplate() {
        String body = formatter.formatBody(this.getClass().getClassLoader().getResource("bodyTemplate.vm").getPath(), createEvent());
        Assert.assertTrue(body.contains("<html>"));
        Assert.assertTrue(body.contains("<b>Hello bar !</b>"));
        Assert.assertTrue(body.contains("<td>other</td>"));
        Assert.assertTrue(body.contains("<td>test</td>"));
        Assert.assertTrue(body.contains("<td>event.topics</td>"));
        Assert.assertTrue(body.contains("<td>foo</td>"));
    }

    @Test
    public void testBodyEventDefinedTemplate() {
        Map<String, Object> data = new HashMap<>();
        data.put("body.template.location", this.getClass().getClassLoader().getResource("propertyBodyTemplate.vm").getPath());
        data.put("test.name", "test");
        data.put("threadCount", 100);
        Event event = new Event("foo", data);
        String body = formatter.formatBody(null, event);
        Assert.assertTrue(body.contains("This is test using property defined template."));
        Assert.assertTrue(body.contains("It seems to work fine for 100"));
    }

}
