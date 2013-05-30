package com.quantumretail.resourcemon;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This attempts to use <a href="https://support.hyperic.com/display/SIGAR/Home">Sigar</a> if available.
 *
 * Sigar uses native libraries to return more accurate (or at least more extensive) system resource information.
 *
 * To install Sigar, include the sigar jar in your classpath (via your pom or project.clj or build.sbt or build.gradle
 * or whatever) and include the native library
 *
 */
public class SigarResourceMonitor implements ResourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(SigarResourceMonitor.class);
    Sigar sigar;

    boolean sigarAvailable = true;

    public SigarResourceMonitor() {
        try {
            this.sigar = new Sigar();
        } catch (Throwable e) {
            log.debug("Error loading Sigar: ", e);
            sigarAvailable = false;
        }
    }

    @Override
    public Map<String, Double> getLoad() {
        Map<String, Double> m = new HashMap<String,Double>();
        if (sigarAvailable) {
            try {
                double idleCpu= sigar.getCpuPerc().getIdle();
                m.put(CPU, 1-idleCpu);
                m.put(CPU+".sigar", 1-idleCpu);
                m.put("CPU.sigar_WAIT", sigar.getCpuPerc().getWait());
                m.put("CPU.sigar_USER", sigar.getCpuPerc().getUser());
                m.put("CPU.sigar_SYS", sigar.getCpuPerc().getSys());
                m.put("CPU.sigar_IDLE", sigar.getCpuPerc().getIdle());

            } catch (UnsatisfiedLinkError e) {
                log.debug("Sigar libraries are not available on the classpath. Start the JVM with -Djava.library.path=<path to Sigar native libraries> to use Sigar");
                sigarAvailable = false;
            } catch (Throwable e) {
                //do something?
                log.error("Error getting stats from Sigar: ", e);
            }
        }
        return m;
    }
}
