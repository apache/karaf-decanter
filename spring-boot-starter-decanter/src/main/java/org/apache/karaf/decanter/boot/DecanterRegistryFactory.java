/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.decanter.boot;

import static org.apache.felix.connect.launch.PojoServiceRegistryFactory.BUNDLE_DESCRIPTORS;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.ServiceLoader;

import org.apache.felix.connect.launch.BundleDescriptor;
import org.apache.felix.connect.launch.ClasspathScanner;
import org.apache.felix.connect.launch.PojoServiceRegistryFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;

public class DecanterRegistryFactory {
    private static final String BUNDLES_CONFIG = "META-INF/decanter.bundles";
    private static final String BUNDLES_CONFIG_DEFAULT = "META-INF/decanter.bundles.default";

    public BundleContext create() throws Exception {
        ServiceLoader<PojoServiceRegistryFactory> loader = ServiceLoader
            .load(PojoServiceRegistryFactory.class);
        PojoServiceRegistryFactory srFactory = loader.iterator().next();
        HashMap<String, Object> pojoSrConfig = new HashMap<>();
        pojoSrConfig.put(BUNDLE_DESCRIPTORS, getBundles());
        return srFactory.newPojoServiceRegistry(pojoSrConfig).getBundleContext();
    }

    List<BundleDescriptor> getBundles() throws URISyntaxException, IOException, Exception {
        URI bundleURL = getURI(BUNDLES_CONFIG);
        if (bundleURL == null) {
            bundleURL = getURI(BUNDLES_CONFIG_DEFAULT);
        }
        List<String> bundleNames = Files.readAllLines(Paths.get(bundleURL), Charset.forName("utf-8"));
        String filter = getBundleFilter(bundleNames);
        FrameworkUtil.createFilter(filter);
        List<BundleDescriptor> bundles = new ClasspathScanner().scanForBundles(filter);
        //printNames(bundles);
        return bundles;
    }

    private URI getURI(String path) throws URISyntaxException {
        ClassLoader loader = this.getClass().getClassLoader();
        URL url = loader.getResource(path); 
        return url == null ? null : url.toURI();
    }
    
    

    static String getBundleFilter(List<String> bundles) {
        StringBuilder joined = new StringBuilder();
        joined.append("(|(Bundle-SymbolicName=");
        boolean first = true;
        for (String bundle : bundles) {
            if (!first) {
                joined.append(")(Bundle-SymbolicName=");
            }
            first = false;
            joined.append(bundle);
        }
        joined.append("))");
        return joined.toString();
    }

    private void printNames(List<BundleDescriptor> bundles) {
        for (BundleDescriptor desc : bundles) {
            System.out.println(desc.getHeaders().get(Constants.BUNDLE_SYMBOLICNAME));
        }
    }
}
