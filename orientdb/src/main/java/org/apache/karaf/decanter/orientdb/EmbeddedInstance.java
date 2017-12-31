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
package org.apache.karaf.decanter.orientdb;

import com.orientechnologies.orient.client.remote.OServerAdmin;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Component(
        immediate = true,
        name = "org.apache.karaf.decanter.orientdb"
)
public class EmbeddedInstance {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmbeddedInstance.class);

    private final static String ORIENTDB_SERVER_CONFIG = "orientdb-server-config.xml";

    private OServer server;

    @Activate
    public void activate() throws Exception {
        LOGGER.info("Starting embedded OrientDB server");
        System.setProperty("ORIENTDB_ROOT_PASSWORD", "decanter");
        Orient.instance().startup();
        server = OServerMain.create();
        server.startup(new File(new File(System.getProperty("karaf.etc")), ORIENTDB_SERVER_CONFIG));
        server.activate();
        OServerAdmin admin = new OServerAdmin("remote:localhost").connect("root", "decanter");
        if (!admin.existsDatabase("decanter", "plocal")) {
            admin.createDatabase("decanter", "document", "plocal");
        }
        admin.close();
    }

    @Deactivate
    public void deactivate() throws Exception {
        LOGGER.info("Shutdown embedded OrientDB server");
        if (server != null) {
            server.shutdown();
        }
        Orient.instance().shutdown();
    }

}
