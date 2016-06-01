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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.felix.connect.launch.BundleDescriptor;
import org.apache.felix.connect.launch.ClasspathScanner;
import org.apache.felix.connect.launch.PojoServiceRegistryFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;

/**
 * Creates a felix connect instance from a list of bundle symbolic names provided in a file.
 * All listed bundles must be present in the project classpath.
 */
public class DecanterRegistryFactory {
    private static final String BUNDLES_CONFIG = "META-INF/decanter.bundles";
    private static final String BUNDLES_CONFIG_DEFAULT = "META-INF/decanter.bundles.default";

    public BundleContext create() throws Exception {
        setSysPropDefault("felix.fileinstall.dir", "etc");
        setSysPropDefault("felix.fileinstall.noInitialDelay", "true");
        PojoServiceRegistryFactory srFactory = ServiceLoader
            .load(PojoServiceRegistryFactory.class).iterator().next();
        HashMap<String, Object> pojoSrConfig = new HashMap<>();
        pojoSrConfig.put(BUNDLE_DESCRIPTORS, getBundles());
        return srFactory.newPojoServiceRegistry(pojoSrConfig).getBundleContext();
    }
    
    private void setSysPropDefault(String key, String defaultValue) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, defaultValue);
        }
    }

    List<BundleDescriptor> getBundles() throws URISyntaxException, IOException, Exception {
        InputStream is = getStream(BUNDLES_CONFIG);
        if (is == null) {
            is = getStream(BUNDLES_CONFIG_DEFAULT);
        }
        List<String> bundleNames = readLines(is);
        String filter = getBundleFilter(bundleNames);
        FrameworkUtil.createFilter(filter);
        List<BundleDescriptor> bundles = new ClasspathScanner().scanForBundles(filter);
        assertAllPresent(bundles, new HashSet<String>(bundleNames));
        //printNames(bundles);
        return bundles;
    }

    private List<String> readLines(InputStream is) throws IOException {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("utf-8")))
            ) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
        
        
    }

    private void assertAllPresent(List<BundleDescriptor> bundles, Set<String> bundleNames) {
        Set<String> bundlesPresent = new HashSet<>();
        for (BundleDescriptor bundle : bundles) {
            bundlesPresent.add(getSymbolicName(bundle)); 
        }
        for (String expected : bundleNames) {
            if (!bundlesPresent.contains(expected)) {
                throw new RuntimeException("Bundle " + expected + " was not loaded");
            }
        }
    }

    private InputStream getStream(String path) throws URISyntaxException {
        return this.getClass().getClassLoader().getResourceAsStream(path);
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

    private String getSymbolicName(BundleDescriptor desc) {
        return desc.getHeaders().get(Constants.BUNDLE_SYMBOLICNAME);
    }
}
