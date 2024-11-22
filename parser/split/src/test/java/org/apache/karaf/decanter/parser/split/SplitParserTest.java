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
package org.apache.karaf.decanter.parser.split;

import org.junit.Assert;
import org.junit.Test;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

public class SplitParserTest {

    private final static String TEST_LINE = "this is a test";

    @Test
    public void noKey() throws Exception {
        SplitParser splitParser = new SplitParser();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("separator", "\\s+");
        splitParser.activate(config);

        Map<String, Object> result = splitParser.parse("line", TEST_LINE);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals("this", result.get("key-0"));
        Assert.assertEquals("is", result.get("key-1"));
        Assert.assertEquals("a", result.get("key-2"));
        Assert.assertEquals("test", result.get("key-3"));
    }

    @Test
    public void validKeys() throws Exception {
        SplitParser splitParser = new SplitParser();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("separator", "\\s+");
        config.put("keys", "this,is,a,test");
        splitParser.activate(config);

        Map<String, Object> result = splitParser.parse("line", TEST_LINE);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals("this", result.get("this"));
        Assert.assertEquals("is", result.get("is"));
        Assert.assertEquals("a", result.get("a"));
        Assert.assertEquals("test", result.get("test"));
    }

    @Test
    public void invalidKeysWithoutDefaultKey() throws Exception {
        SplitParser splitParser = new SplitParser();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("separator", "\\s+");
        config.put("keys", "this,is");
        config.put("useDefaultKey", "false");
        splitParser.activate(config);

        Map<String, Object> result = splitParser.parse("line", TEST_LINE);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void invalidKeysWithDefaultKey() throws Exception {
        SplitParser splitParser = new SplitParser();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("separator", "\\s+");
        config.put("keys", "this,is");
        config.put("useDefaultKey", "true");
        splitParser.activate(config);

        Map<String, Object> result = splitParser.parse("line", TEST_LINE);
        Assert.assertEquals(4, result.size());
        Assert.assertEquals("this", result.get("key-0"));
        Assert.assertEquals("is", result.get("key-1"));
        Assert.assertEquals("a", result.get("key-2"));
        Assert.assertEquals("test", result.get("key-3"));
    }

}
