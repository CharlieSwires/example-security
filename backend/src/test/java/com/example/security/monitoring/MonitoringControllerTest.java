/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.security.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MonitoringControllerTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsMissingOrIncorrectKeyWithoutCapturingMetrics() {
        MonitoringSnapshotService service = mock(MonitoringSnapshotService.class);
        MonitoringController controller = new MonitoringController(service, KEY);

        assertThat(controller.snapshot(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.snapshot("wrong").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void returnsSnapshotForCorrectKey() {
        MonitoringSnapshotService service = mock(MonitoringSnapshotService.class);
        MonitoringSnapshot expected = new MonitoringSnapshot(
                Instant.EPOCH, "node-1", 10, 2, 1, 2, 3, 4, 75,
                5, 6, 7, 2, 71, true, 8);
        when(service.capture()).thenReturn(expected);
        MonitoringController controller = new MonitoringController(service, KEY);

        var response = controller.snapshot(KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        verify(service).capture();
    }

    @Test
    void refusesWeakMonitoringSecretAtStartup() {
        MonitoringSnapshotService service = mock(MonitoringSnapshotService.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new MonitoringController(service, "replace-with-a-key"))
                .isInstanceOf(IllegalStateException.class);
    }
}
