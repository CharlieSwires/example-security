/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.security.monitoring;

import java.time.Instant;

public record MonitoringSnapshot(
        Instant timestamp,
        String instanceId,
        long uptimeSeconds,
        int processors,
        double processCpuPercent,
        double systemCpuPercent,
        long heapUsedBytes,
        long heapMaxBytes,
        double heapUsedPercent,
        long nonHeapUsedBytes,
        int liveThreads,
        long filesystemTotalBytes,
        long filesystemUsableBytes,
        double filesystemUsedPercent,
        boolean mongoReachable,
        long mongoPingMillis
) {
}
