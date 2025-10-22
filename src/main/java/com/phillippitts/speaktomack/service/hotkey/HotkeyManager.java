package com.phillippitts.speaktomack.service.hotkey;

import com.phillippitts.speaktomack.config.properties.HotkeyProperties;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyConflictEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPermissionDeniedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Registers a global key hook and translates native events into domain hotkey events
 * using a configured HotkeyTrigger.
 * Tests should inject a fake GlobalKeyHook and emit NormalizedKeyEvent instances
 * directly to the registered listener.
 */
@Service
public class HotkeyManager implements SmartLifecycle {

    private static final Logger LOG = LogManager.getLogger(HotkeyManager.class);

    private final GlobalKeyHook hook;
    private final HotkeyTrigger trigger;
    private final ApplicationEventPublisher publisher;
    private final HotkeyProperties props;

    private volatile boolean running;

    public HotkeyManager(GlobalKeyHook hook,
                         HotkeyTriggerFactory factory,
                         HotkeyProperties props,
                         ApplicationEventPublisher publisher) {
        this.hook = hook;
        this.trigger = factory.from(props);
        this.props = props;
        this.publisher = publisher;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            hook.addListener(dispatcher());
            hook.register();
            running = true;
            LOG.info("HotkeyManager started with trigger={}", trigger.name());
            detectReservedConflict();
        } catch (SecurityException se) {
            LOG.warn("Global key hook permission denied: {}", se.toString());
            publisher.publishEvent(new HotkeyPermissionDeniedEvent(Instant.now()));
        } catch (Exception e) {
            LOG.error("Failed to start HotkeyManager", e);
            // do not rethrow to avoid crashing app; user can still operate via UI later
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        try {
            hook.unregister();
        } catch (Exception ignored) {
            // Ignore
        }
        running = false;
        LOG.info("HotkeyManager stopped");
    }

    @PreDestroy
    public void shutdown() {
        // Defensive unregistration on container shutdown
        stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private Consumer<NormalizedKeyEvent> dispatcher() {
        return e -> {
            boolean matched = switch (e.type()) {
                case PRESSED -> trigger.onKeyPressed(e);
                case RELEASED -> trigger.onKeyReleased(e);
            };

            if (matched) {
                if (e.type() == NormalizedKeyEvent.Type.PRESSED) {
                    publisher.publishEvent(new HotkeyPressedEvent(Instant.now()));
                } else {
                    publisher.publishEvent(new HotkeyReleasedEvent(Instant.now()));
                }
            }
        };
    }

    private void detectReservedConflict() {
        // Compare configured hotkey against reserved list from properties
        String key = props.getKey();
        Set<String> mods = Set.copyOf(props.getModifiers().stream()
                .map(m -> m.toUpperCase(Locale.ROOT)).toList());
        for (String spec : props.getReserved()) {
            if (com.phillippitts.speaktomack.service.hotkey.KeyNameMapper.matchesReserved(mods, key, spec)) {
                publisher.publishEvent(new HotkeyConflictEvent(props.getKey(),
                        List.copyOf(props.getModifiers()), Instant.now()));
                LOG.warn("Configured hotkey '{}' + {} conflicts with reserved '{}'", key, mods, spec);
                break;
            }
        }
    }
}
