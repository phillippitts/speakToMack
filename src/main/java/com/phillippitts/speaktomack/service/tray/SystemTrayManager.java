package com.phillippitts.speaktomack.service.tray;

import com.phillippitts.speaktomack.service.orchestration.ApplicationState;
import com.phillippitts.speaktomack.service.orchestration.RecordingService;
import com.phillippitts.speaktomack.service.orchestration.event.ApplicationStateChangedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.phillippitts.speaktomack.service.livecaption.LiveCaptionManager;

import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a macOS system tray icon with Start/Stop Recording and Quit controls.
 *
 * <p>Reacts to {@link ApplicationStateChangedEvent} to update menu text, tooltip,
 * and icon color (gray=idle, green=recording, yellow=transcribing).
 *
 * @since 1.2
 */
@Service
@ConditionalOnProperty(name = "tray.enabled", matchIfMissing = true)
public class SystemTrayManager implements SmartLifecycle {

    private static final Logger LOG = LogManager.getLogger(SystemTrayManager.class);

    private final RecordingService recordingService;
    private final ApplicationContext applicationContext;
    private final Optional<LiveCaptionManager> liveCaptionManager;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile TrayIcon trayIcon;
    private volatile MenuItem startItem;
    private volatile MenuItem stopItem;

    public SystemTrayManager(RecordingService recordingService,
                             ApplicationContext applicationContext,
                             Optional<LiveCaptionManager> liveCaptionManager) {
        this.recordingService = recordingService;
        this.applicationContext = applicationContext;
        this.liveCaptionManager = liveCaptionManager;
    }

    @Override
    public void start() {
        if (!SystemTray.isSupported()) {
            LOG.warn("System tray not supported on this platform");
            return;
        }
        running.set(true);
        SwingUtilities.invokeLater(this::initializeTray);
    }

    private void initializeTray() {
        try {
            PopupMenu popup = new PopupMenu();

            startItem = new MenuItem("Start Recording");
            startItem.addActionListener(e -> onStartClicked());
            popup.add(startItem);

            stopItem = new MenuItem("Stop Recording");
            stopItem.setEnabled(false);
            stopItem.addActionListener(e -> onStopClicked());
            popup.add(stopItem);

            liveCaptionManager.ifPresent(manager -> {
                popup.addSeparator();
                CheckboxMenuItem captionItem = new CheckboxMenuItem("Live Caption");
                captionItem.setState(manager.isEnabled());
                captionItem.addItemListener(e ->
                        Thread.ofVirtual().start(() -> manager.setEnabled(captionItem.getState())));
                popup.add(captionItem);
            });

            popup.addSeparator();

            MenuItem quitItem = new MenuItem("Quit");
            quitItem.addActionListener(e -> onQuitClicked());
            popup.add(quitItem);

            Image icon = createIcon(Color.GRAY);
            trayIcon = new TrayIcon(icon, "SpeakToMack - Idle", popup);
            trayIcon.setImageAutoSize(true);

            SystemTray.getSystemTray().add(trayIcon);
            LOG.info("System tray icon initialized");
        } catch (AWTException e) {
            LOG.error("Failed to add system tray icon", e);
        }
    }

    private void onStartClicked() {
        Thread.ofVirtual().start(() -> {
            if (!recordingService.startRecording()) {
                LOG.debug("Start recording rejected (already recording or error)");
            }
        });
    }

    private void onStopClicked() {
        Thread.ofVirtual().start(() -> {
            if (!recordingService.stopRecording()) {
                LOG.debug("Stop recording rejected (not recording)");
            }
        });
    }

    private void onQuitClicked() {
        LOG.info("Quit requested from system tray");
        Thread.ofVirtual().start(() -> SpringApplication.exit(applicationContext, () -> 0));
    }

    @EventListener
    public void onStateChanged(ApplicationStateChangedEvent event) {
        if (trayIcon == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> updateTrayForState(event.current()));
    }

    private void updateTrayForState(ApplicationState state) {
        switch (state) {
            case IDLE -> {
                trayIcon.setImage(createIcon(Color.GRAY));
                trayIcon.setToolTip("SpeakToMack - Idle");
                startItem.setEnabled(true);
                stopItem.setEnabled(false);
            }
            case RECORDING -> {
                trayIcon.setImage(createIcon(Color.GREEN));
                trayIcon.setToolTip("SpeakToMack - Recording...");
                startItem.setEnabled(false);
                stopItem.setEnabled(true);
            }
            case TRANSCRIBING -> {
                trayIcon.setImage(createIcon(Color.YELLOW));
                trayIcon.setToolTip("SpeakToMack - Transcribing...");
                startItem.setEnabled(false);
                stopItem.setEnabled(false);
            }
        }
    }

    /**
     * Creates a simple 16x16 filled circle icon with the given color.
     */
    static Image createIcon(Color color) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(1, 1, 14, 14);
        g2.dispose();
        return img;
    }

    @Override
    public void stop() {
        running.set(false);
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
            LOG.info("System tray icon removed");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // Start last, stop first
    }
}
