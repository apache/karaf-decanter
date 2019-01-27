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
package org.apache.karaf.decanter.appender.elasticsearch.jest;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.config.HttpClientConfig.Builder;
import io.searchbox.core.Index;

@Component(
    name ="org.apache.karaf.decanter.appender.elasticsearch.jest",
    immediate = true,
    property=EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class ElasticsearchAppender implements EventHandler {

    @Reference
    public Marshaller marshaller;

    public static String ADDRESS_PROPERTY = "address";
    public static String USERNAME_PROPERTY = "username";
    public static String PASSWORD_PROPERTY = "password";
    public static String INDEX_PREFIX_PROPERTY = "index.prefix";
    public static String INDEX_TYPE_PROPERTY = "index.type";
    public static String INDEX_EVENT_TIMESTAMPED_PROPERTY = "index.event.timestamped";

    public static String ADDRESS_DEFAULT = "http://localhost:9200";
    public static String USERNAME_DEFAULT = null;
    public static String PASSWORD_DEFAULT = null;
    public static String INDEX_PREFIX_DEFAULT = "karaf";
    public static String INDEX_TYPE_DEFAULT = "decanter";
    public static String INDEX_EVENT_TIMESTAMPED_DEFAULT = "true";

    private Dictionary<String, Object> config;

    private JestClient client;

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
    private final SimpleDateFormat indexDateFormat = new SimpleDateFormat("yyyy.MM.dd");

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) {
        this.config = config;
        String addressesString = getValue(config, ADDRESS_PROPERTY, ADDRESS_DEFAULT);
        Set<String> addresses = new HashSet<String>(Arrays.asList(addressesString.split(";")));
        String username = getValue(config, USERNAME_PROPERTY, USERNAME_DEFAULT);
        String password = getValue(config, PASSWORD_PROPERTY, PASSWORD_DEFAULT);
        Builder builder = new HttpClientConfig.Builder(addresses).readTimeout(10000)
            .multiThreaded(true);
        
        if (addresses.size() > 1) {
            builder = builder
                    .discoveryEnabled(true)
                    .discoveryFrequency(1l, TimeUnit.MINUTES);
        } else {
            builder.discoveryEnabled(false);
        }

        for (String address : addresses) {
            if (address.startsWith("https")) {
                try {
                    SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
                    sslContextBuilder.loadTrustMaterial(new TrustAny());
                    SSLContext sslContext = sslContextBuilder.build();
                    HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
                    SSLConnectionSocketFactory sslSocketFactory = 
                            new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
                    builder.defaultSchemeForDiscoveredNodes("https").sslSocketFactory(sslSocketFactory);
                } catch (KeyManagementException | KeyStoreException | NoSuchAlgorithmException ex) {
                    throw new RuntimeException("SSL exception when connect to ElasticSearch", ex);
                }

                break;
            }
        }

        if (username != null) {
            builder = builder.defaultCredentials(username, password);
        }
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(builder.build());
        client = factory.getObject();
        TimeZone tz = TimeZone.getTimeZone( "UTC" );
        tsFormat.setTimeZone(tz);
        indexDateFormat.setTimeZone(tz);
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Deactivate
    public void close() {
        client.shutdownClient();
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            try {
                send(event);
            } catch (Exception e) {
                LOGGER.warn("Can't append into Elasticsearch", e);
            }
        }
    }

    private void send(Event event) throws Exception {
        String indexName = getIndexName(getValue(config, INDEX_PREFIX_PROPERTY, INDEX_PREFIX_DEFAULT), getDate(event));
        String jsonSt = marshaller.marshal(event);

        JestResult result = client.execute(new Index.Builder(jsonSt).index(indexName).type(getValue(config, INDEX_TYPE_PROPERTY, INDEX_TYPE_DEFAULT)).build());

        if (!result.isSucceeded()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
    }

    private Date getDate(Event event) {
        Long ts = (Long)event.getProperty("timestamp");
        Date date = ts != null ? new Date(ts) : new Date();
        return date;
    }

    private String getIndexName(String prefix, Date date) {
        boolean indexTimestamped = Boolean.parseBoolean(getValue(config, INDEX_EVENT_TIMESTAMPED_PROPERTY, INDEX_EVENT_TIMESTAMPED_DEFAULT));
        if (indexTimestamped) {
            return prefix + "-" + indexDateFormat.format(date);
        } else {
            return prefix;
        }
    }

    private class TrustAny implements TrustStrategy {

        public TrustAny() {
            super();
        }

        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            return true;
        }
    }

    private class EsHostnameVerifier implements HostnameVerifier {

        private final HostnameVerifier delegate;
        private final String passHostname;

        public EsHostnameVerifier(String passHostname) {
            super();
            this.delegate = new DefaultHostnameVerifier();
            this.passHostname = passHostname == null || passHostname.length() == 0
                    ? null : passHostname;
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return (passHostname != null && delegate.verify(passHostname, session));
        }

    }

}
