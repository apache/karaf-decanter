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


import org.apache.karaf.decanter.api.parser.Parser;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.parser.split",
        immediate = true,
        property = Parser.SERVICE_KEY_ID + "=split")
public class SplitParser implements Parser {

    private final static Logger LOGGER = LoggerFactory.getLogger(SplitParser.class);

    private String separator;
    private String keys = null;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) {
        this.separator = (config.get("separator") != null) ? (String) config.get("separator") : ",";
        this.keys = (config.get("keys") != null) ? (String) config.get("keys") : null;
    }

    @Override
    public Map<String, Object> parse(String key, String line) {
        Map<String, Object> map = new HashMap<>();
        if (line != null) {
            String[] valuesArray = line.split(separator);
            String[] keysArray;

            if (this.keys != null) {
                keysArray = this.keys.split(",");
                if (keysArray.length != valuesArray.length) {
                    LOGGER.warn("keys count and values count don't match, using default keys ID");
                    keysArray = new String[valuesArray.length];
                    for (int i = 0; i < valuesArray.length; i++) {
                        keysArray[i] = "key-" + i;
                    }
                }
            } else {
                keysArray = new String[valuesArray.length];
                for (int i = 0; i < valuesArray.length; i++) {
                    keysArray[i] = "key-" + i;
                }
            }
            for (int i = 0; i < valuesArray.length; i++) {
                map.put(keysArray[i], valuesArray[i]);
            }
        }
        return map;
    }

}
