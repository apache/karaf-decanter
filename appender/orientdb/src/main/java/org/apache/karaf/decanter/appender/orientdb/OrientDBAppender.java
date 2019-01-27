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
package org.apache.karaf.decanter.appender.orientdb;


import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.Dictionary;

@Component(
      name = "org.apache.karaf.decanter.appender.orientdb",
      immediate = true,
      property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class OrientDBAppender implements EventHandler {

    public static String URL_PROPERTY = "url";
    public static String USERNAME_PROPERTY = "username";
    public static String PASSWORD_PROPERTY = "password";

    public static String URL_DEFAULT = "remote:localhost/decanter";
    public static String USERNAME_DEFAULT = "root";
    public static String PASSWORD_DEFAULT = "decanter";

    @Reference
    public Marshaller marshaller;

    private ODatabaseDocumentTx database;

    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) {
        config = componentContext.getProperties();
        String url = getValue(config, URL_PROPERTY, URL_DEFAULT);
        String username = getValue(config, USERNAME_PROPERTY, USERNAME_DEFAULT);
        String password = getValue(config, PASSWORD_PROPERTY, PASSWORD_DEFAULT);
        database = new ODatabaseDocumentTx(url).open(username, password);
    }

    @Deactivate
    public void deactivate() {
        database.close();
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            String json = marshaller.marshal(event);
            ODocument document = new ODocument("decanter").fromJSON(json);
            document.save();
        }
    }

}
