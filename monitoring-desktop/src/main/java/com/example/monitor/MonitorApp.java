/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSession;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

public final class MonitorApp {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");

    private final MonitorConfig config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final TwilioSmsClient sms;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final Map<String, MonitoringSnapshot> nodes = new LinkedHashMap<>();
    private final Map<String, Boolean> activeAlerts = new LinkedHashMap<>();
    private final Map<String, Integer> missingNodeChecks = new LinkedHashMap<>();

    private JFrame frame;
    private JLabel overall;
    private DefaultTableModel tableModel;
    private JTextArea history;
    private int consecutivePublicFailures;

    private MonitorApp(MonitorConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.requestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.json = new ObjectMapper().registerModule(new JavaTimeModule());
        this.sms = new TwilioSmsClient(config, http);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "monitor-poll");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static void main(String[] args) {
        Path configPath = args.length == 0 ? Path.of("monitor.properties") : Path.of(args[0]);
        try {
            MonitorConfig config = MonitorConfig.load(configPath);
            SwingUtilities.invokeLater(() -> new MonitorApp(config).start());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Monitor configuration error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void start() {
        buildUi();
        scheduler.scheduleWithFixedDelay(this::checkSafely, 0,
                config.pollInterval().toSeconds(), TimeUnit.SECONDS);
    }

    private void buildUi() {
        frame = new JFrame("Example Security Monitor");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(950, 650));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        overall = new JLabel("Starting monitoring checks…");
        overall.setOpaque(true);
        overall.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        top.add(overall, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton checkNow = new JButton("Check now");
        checkNow.addActionListener(e -> scheduler.execute(this::checkSafely));
        JButton testSms = new JButton("Send test SMS");
        testSms.setEnabled(config.smsEnabled());
        testSms.addActionListener(e -> scheduler.execute(this::sendTestSms));
        actions.add(checkNow);
        actions.add(testSms);
        top.add(actions, BorderLayout.EAST);

        tableModel = new DefaultTableModel(
                new Object[]{"Component", "Status", "Value", "Warning level", "Last checked"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);

        history = new JTextArea();
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(history));
        split.setResizeWeight(0.62);
        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void checkSafely() {
        if (!checking.compareAndSet(false, true)) return;
        try {
            PublicResult publicResult = checkPublic();
            collectNodeSamples();
            evaluateAndDisplay(publicResult);
        } catch (Exception ex) {
            append("Monitoring cycle failed: " + safeMessage(ex));
        } finally {
            checking.set(false);
        }
    }

    private PublicResult checkPublic() {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.healthUrl()))
                    .timeout(config.requestTimeout())
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            boolean up = response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
            long certificateDays = certificateDays(response.sslSession().orElse(null));
            if (up) consecutivePublicFailures = 0; else consecutivePublicFailures++;
            return new PublicResult(up, response.statusCode(), millis, certificateDays, null);
        } catch (Exception ex) {
            consecutivePublicFailures++;
            return new PublicResult(false, 0,
                    Duration.ofNanos(System.nanoTime() - start).toMillis(), -1, safeMessage(ex));
        }
    }

    private void collectNodeSamples() {
        for (int i = 0; i < config.snapshotSamples(); i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.snapshotUrl()))
                        .timeout(config.requestTimeout())
                        .header("Accept", "application/json")
                        .header("X-Monitoring-Key", config.monitoringKey())
                        .GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    MonitoringSnapshot snapshot = json.readValue(response.body(), MonitoringSnapshot.class);
                    synchronized (nodes) { nodes.put(snapshot.instanceId(), snapshot); }
                } else {
                    append("Snapshot endpoint returned HTTP " + response.statusCode());
                }
            } catch (Exception ex) {
                append("Snapshot check failed: " + safeMessage(ex));
            }
        }
    }

    private void evaluateAndDisplay(PublicResult external) {
        boolean publicAlert = consecutivePublicFailures >= config.failuresBeforeAlert();
        transition("public-down", publicAlert,
                "CRITICAL: Example Security is unavailable (HTTP " + external.statusCode() + ")",
                "RECOVERY: Example Security is responding again in " + external.responseMillis() + " ms");
        transition("public-slow", external.up() && external.responseMillis() > config.slowResponseMillis(),
                "WARNING: Example Security response time is " + external.responseMillis() + " ms",
                "RECOVERY: Example Security response time is back to " + external.responseMillis() + " ms");
        transition("certificate", external.certificateDays() >= 0
                        && external.certificateDays() < config.certificateWarningDays(),
                "WARNING: Example Security TLS certificate expires in " + external.certificateDays() + " days",
                "RECOVERY: Example Security TLS certificate is no longer near expiry");

        Map<String, MonitoringSnapshot> current;
        synchronized (nodes) { current = new LinkedHashMap<>(nodes); }
        Instant staleBefore = Instant.now().minus(config.pollInterval().multipliedBy(3));
        for (String expected : config.expectedInstanceIds()) {
            MonitoringSnapshot observed = current.get(expected);
            boolean missing = observed == null || observed.timestamp().isBefore(staleBefore);
            int missingChecks = missing ? missingNodeChecks.getOrDefault(expected, 0) + 1 : 0;
            missingNodeChecks.put(expected, missingChecks);
            transition(expected + "-missing", missingChecks >= config.failuresBeforeAlert(),
                    "CRITICAL: No recent monitoring snapshot from " + expected,
                    "RECOVERY: Monitoring snapshots resumed from " + expected);
        }
        for (MonitoringSnapshot node : current.values()) {
            transition(node.instanceId() + "-mongo", !node.mongoReachable(),
                    "CRITICAL: MongoDB ping failed from " + node.instanceId(),
                    "RECOVERY: MongoDB is reachable from " + node.instanceId());
            transition(node.instanceId() + "-heap", node.heapUsedPercent() >= config.heapWarningPercent(),
                    "WARNING: JVM heap on " + node.instanceId() + " is " + fmt(node.heapUsedPercent()) + "%",
                    "RECOVERY: JVM heap on " + node.instanceId() + " is back below threshold");
            transition(node.instanceId() + "-disk", node.filesystemUsedPercent() >= config.diskWarningPercent(),
                    "WARNING: Container filesystem on " + node.instanceId() + " is "
                            + fmt(node.filesystemUsedPercent()) + "% full",
                    "RECOVERY: Container filesystem on " + node.instanceId() + " is back below threshold");
        }

        SwingUtilities.invokeLater(() -> refreshTable(external, current));
    }

    private void refreshTable(PublicResult external, Map<String, MonitoringSnapshot> current) {
        tableModel.setRowCount(0);
        addRow("Public HTTPS /health", external.up() ? "UP" : "DOWN",
                external.statusCode() == 0 ? external.error() : "HTTP " + external.statusCode(),
                config.failuresBeforeAlert() + " failed checks", Instant.now());
        addRow("External response time", external.responseMillis() <= config.slowResponseMillis() ? "OK" : "SLOW",
                external.responseMillis() + " ms", "> " + config.slowResponseMillis() + " ms", Instant.now());
        String certificateStatus = external.certificateDays() < 0 ? "UNKNOWN"
                : external.certificateDays() < config.certificateWarningDays() ? "WARN" : "OK";
        addRow("TLS certificate", certificateStatus,
                external.certificateDays() < 0 ? "not available" : external.certificateDays() + " days remaining",
                "< " + config.certificateWarningDays() + " days", Instant.now());
        for (MonitoringSnapshot node : current.values()) {
            addRow(node.instanceId() + " application", "UP", "uptime " + duration(node.uptimeSeconds()), "n/a", node.timestamp());
            addRow(node.instanceId() + " MongoDB", node.mongoReachable() ? "UP" : "DOWN",
                    node.mongoPingMillis() + " ms", "must be UP", node.timestamp());
            addRow(node.instanceId() + " JVM heap", node.heapUsedPercent() < config.heapWarningPercent() ? "OK" : "WARN",
                    fmt(node.heapUsedPercent()) + "% (" + mib(node.heapUsedBytes()) + " / " + mib(node.heapMaxBytes()) + " MiB)",
                    ">= " + fmt(config.heapWarningPercent()) + "%", node.timestamp());
            addRow(node.instanceId() + " process CPU", "INFO", fmt(node.processCpuPercent()) + "%", "trend", node.timestamp());
            addRow(node.instanceId() + " system CPU", "INFO", fmt(node.systemCpuPercent()) + "%", "trend", node.timestamp());
            addRow(node.instanceId() + " filesystem", node.filesystemUsedPercent() < config.diskWarningPercent() ? "OK" : "WARN",
                    fmt(node.filesystemUsedPercent()) + "% used", ">= " + fmt(config.diskWarningPercent()) + "%", node.timestamp());
            addRow(node.instanceId() + " threads", "INFO", Integer.toString(node.liveThreads()), "trend", node.timestamp());
        }
        boolean critical = activeAlerts.keySet().stream().anyMatch(k -> k.contains("public-down")
                || k.endsWith("-mongo") || k.endsWith("-missing"));
        overall.setText((activeAlerts.isEmpty() ? "ALL CHECKS NORMAL" : activeAlerts.size() + " ACTIVE ALERT(S)")
                + " — last cycle " + TIME.format(Instant.now()));
        overall.setBackground(activeAlerts.isEmpty() ? new Color(197, 239, 203)
                : critical ? new Color(255, 190, 190) : new Color(255, 230, 160));
    }

    private void transition(String key, boolean active, String alarm, String recovery) {
        boolean wasActive = activeAlerts.getOrDefault(key, false);
        if (active && !wasActive) {
            activeAlerts.put(key, true);
            append(alarm);
            sendAlert(key, alarm, false);
        } else if (!active && wasActive) {
            activeAlerts.remove(key);
            append(recovery);
            sendAlert(key + "-recovery", recovery, false);
        }
    }

    private void sendAlert(String key, String message, boolean bypassCooldown) {
        try {
            if (sms.send(key, message, bypassCooldown)) append("SMS queued: " + message);
        } catch (Exception ex) {
            append("SMS delivery failed: " + safeMessage(ex));
        }
    }

    private void sendTestSms() {
        sendAlert("manual-test", "TEST: Example Security desktop monitoring SMS is working", true);
    }

    private void addRow(String component, String status, String value, String threshold, Instant checked) {
        tableModel.addRow(new Object[]{component, status, value, threshold, TIME.format(checked)});
    }

    private void append(String message) {
        SwingUtilities.invokeLater(() -> {
            history.append(TIME.format(Instant.now()) + "  " + message + System.lineSeparator());
            history.setCaretPosition(history.getDocument().getLength());
        });
    }

    private static long certificateDays(SSLSession session) {
        if (session == null) return -1;
        try {
            if (session.getPeerCertificates()[0] instanceof X509Certificate cert) {
                return ChronoUnit.DAYS.between(Instant.now(), cert.getNotAfter().toInstant());
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static String fmt(double value) { return value < 0 ? "n/a" : ONE_DECIMAL.format(value); }
    private static long mib(long bytes) { return bytes < 0 ? -1 : bytes / (1024 * 1024); }
    private static String duration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        return days + "d " + hours + "h " + minutes + "m";
    }

    private record PublicResult(boolean up, int statusCode, long responseMillis,
                                long certificateDays, String error) { }
}
