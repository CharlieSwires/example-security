/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.security.monitoring;

import com.sun.management.OperatingSystemMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class MonitoringSnapshotService {

    private final MongoTemplate mongoTemplate;
    private final String instanceId;

    public MonitoringSnapshotService(
            MongoTemplate mongoTemplate,
            @Value("${app.monitoring.instance-id:unknown-instance}") String instanceId) {
        this.mongoTemplate = mongoTemplate;
        this.instanceId = instanceId == null || instanceId.isBlank() ? "unknown-instance" : instanceId.trim();
    }

    public MonitoringSnapshot capture() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        double processCpu = -1;
        double systemCpu = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
            processCpu = percent(os.getProcessCpuLoad());
            systemCpu = percent(os.getCpuLoad());
        }

        File filesystem = new File("/");
        long total = filesystem.getTotalSpace();
        long usable = filesystem.getUsableSpace();
        double diskUsed = total <= 0 ? -1 : ((double) (total - usable) * 100.0) / total;

        boolean mongoReachable = false;
        long mongoStarted = System.nanoTime();
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            mongoReachable = true;
        } catch (RuntimeException ignored) {
            // The boolean is intentionally returned instead of database error details.
        }
        long mongoMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - mongoStarted);

        return new MonitoringSnapshot(
                Instant.now(),
                instanceId,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                runtime.availableProcessors(),
                processCpu,
                systemCpu,
                heap.getUsed(),
                heap.getMax(),
                ratio(heap.getUsed(), heap.getMax()),
                nonHeap.getUsed(),
                threads.getThreadCount(),
                total,
                usable,
                diskUsed,
                mongoReachable,
                mongoMillis
        );
    }

    private static double percent(double fraction) {
        return fraction < 0 ? -1 : fraction * 100.0;
    }

    private static double ratio(long used, long maximum) {
        return maximum <= 0 ? -1 : ((double) used * 100.0) / maximum;
    }
}
