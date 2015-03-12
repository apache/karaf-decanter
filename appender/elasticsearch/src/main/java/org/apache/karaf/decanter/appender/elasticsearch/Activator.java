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
package org.apache.karaf.decanter.appender.elasticsearch;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.karaf.decanter.api.Appender;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    private ElasticsearchAppender appender;

    public void start(BundleContext bundleContext) {
        // TODO embed mode and configuration admin support for location of Elasticsearch
        appender = new ElasticsearchAppender("localhost", 9300);
        appender.open();
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put("name", "elasticsearch");
        bundleContext.registerService(Appender.class, appender, properties);
    }

    public void stop(BundleContext bundleContext) {
        appender.close();;
    }

}
