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
package org.apache.karaf.decanter.alerting.service.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class LoaderTest {

    @Test
    public void testLoad() throws Exception {
        List<Rule> rules = Loader.load(null);

        Assert.assertEquals(0, rules.size());

        Dictionary<String, Object> configuration = new Hashtable<>();

        rules = Loader.load(configuration);
        Assert.assertEquals(0, rules.size());

        configuration.put("foo", "bar");
        rules = Loader.load(configuration);
        Assert.assertEquals(0, rules.size());

        configuration.put("rule.1", "{\"condition\":\"foo:bar AND @date:[X TO Y]\",\"period\":\"1MINUTE\",\"level\":\"CRITICAL\",\"recoverable\":true}");
        configuration.put("rule.2", "{\"condition\":\"foo:bar AND NOT other:te*\",\"period\":\"1HOUR\",\"level\":\"CRITICAL\",\"recoverable\":false}");
        configuration.put("rule.3", "{\"condition\":\"threadCount:[0 TO 200]\",\"level\":\"SEVERE\"}");
        rules = Loader.load(configuration);
        Assert.assertEquals(3, rules.size());

        Rule rule1 = rules.get(2);
        Assert.assertEquals("1", rule1.getName());
        Assert.assertEquals("foo:bar AND @date:[X TO Y]", rule1.getCondition());
        Assert.assertEquals("1MINUTE", rule1.getPeriod());
        Assert.assertEquals("CRITICAL", rule1.getLevel());
        Assert.assertEquals(true, rule1.isRecoverable());

        Rule rule2 = rules.get(1);
        Assert.assertEquals("2", rule2.getName());
        Assert.assertEquals("foo:bar AND NOT other:te*", rule2.getCondition());
        Assert.assertEquals("1HOUR", rule2.getPeriod());
        Assert.assertEquals("CRITICAL", rule2.getLevel());
        Assert.assertEquals(false, rule2.isRecoverable());

        Rule rule3 = rules.get(0);
        Assert.assertEquals("3", rule3.getName());
        Assert.assertEquals("threadCount:[0 TO 200]", rule3.getCondition());
        Assert.assertNull(rule3.getPeriod());
        Assert.assertEquals("SEVERE", rule3.getLevel());
        Assert.assertEquals(false, rule3.isRecoverable());

        configuration = new Hashtable<>();
        configuration.put("rule.empty", "");
        rules = Loader.load(configuration);
        Assert.assertEquals(0, rules.size());
    }

}
