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
package org.apache.karaf.decanter.parser.regex;

import org.apache.karaf.decanter.api.parser.Parser;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(
        name = "org.apache.karaf.decanter.parser.regex",
        immediate = true,
        property = Parser.SERVICE_KEY_ID + "=regex"
)
public class RegexParser implements Parser {

    private final static Logger LOGGER = LoggerFactory.getLogger(RegexParser.class);

    private Pattern pattern;
    private String keys = null;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) {
        String regex = (config.get("regex") != null) ? (String) config.get("regex") : "(.*)";
        this.pattern = Pattern.compile(regex);
        this.keys = (config.get("keys") != null) ? (String) config.get("keys") : null;
    }

    @Override
    public Map<String, Object> parse(String key, String line) {
        Map<String, Object> data = new HashMap<>();
        if (line != null) {
            Matcher matcher = pattern.matcher(line);
            String[] keysArray;
            if (keys != null) {
                keysArray = keys.split(",");
                if (keysArray.length != matcher.groupCount()) {
                    LOGGER.warn("keys count and regex groups count don't match, using default keys ID");
                    keysArray = new String[matcher.groupCount()];
                    for (int i = 0; i < keysArray.length; i++) {
                        keysArray[i] = "key-" + i;
                    }
                }
            } else {
                keysArray = new String[matcher.groupCount()];
                for (int i = 0; i < keysArray.length; i++) {
                    keysArray[i] = "key-" + i;
                }
            }
            if (matcher.find()) {
                for (int i = 0; i < matcher.groupCount(); i++) {
                    try {
                        data.put(keysArray[i], Integer.parseInt(matcher.group(i + 1)));
                        continue;
                    } catch (Exception e) {
                        // nothing to do
                    }

                    try {
                        data.put(keysArray[i], Long.parseLong(matcher.group(i + 1)));
                        continue;
                    } catch (Exception e) {
                        // nothing to do
                    }
                    data.put(keysArray[i], matcher.group(i + 1));
                }
            }
        }
        return data;
    }
}
