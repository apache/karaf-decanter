package org.apache.karaf.decanter.marshaller.json;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        Dictionary<String, String> props = new Hashtable<>();
        props.put(Marshaller.SERVICE_KEY_DATAFORMAT, "json");
        context.registerService(Marshaller.class, new JsonMarshaller(), props);
        context.registerService(Unmarshaller.class, new JsonUnmarshaller(), props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {

    }

}
