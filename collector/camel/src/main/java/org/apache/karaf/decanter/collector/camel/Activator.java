package org.apache.karaf.decanter.collector.camel;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {

	private ServiceTracker<EventAdmin, ServiceRegistration> tracker;

    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<EventAdmin, ServiceRegistration>(bundleContext, EventAdmin.class, null) {

            @Override
            public ServiceRegistration<?> addingService(ServiceReference<EventAdmin> reference) {
                EventAdmin eventAdmin = bundleContext.getService(reference);
                CamelCollector collector = new CamelCollector(eventAdmin);
                Dictionary<String, String> properties = new Hashtable<String, String>();
                properties.put("decanter.collector.name", "camel");
                return bundleContext.registerService(Runnable.class, collector, properties);
            }

            @Override
            public void removedService(ServiceReference<EventAdmin> reference, ServiceRegistration reg) {
                reg.unregister();
                super.removedService(reference, reg);
            }
            
        };
        tracker.open();
    }

    public void stop(BundleContext bundleContext) throws Exception {
        tracker.close();
    }
	
}
