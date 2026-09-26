package dev.frostguard.engine.emulator.instance;

import com.android.ddmlib.IDevice;
import dev.frostguard.engine.emulator.BoundedProcessRunner;
import dev.frostguard.engine.emulator.EmulatorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Connects Frostguard to Android SDK emulators managed by an external Linux launcher.
 * Profile slot zero maps to {@code emulator-5554}; each subsequent slot advances by two ports.
 */
public final class AndroidSdkEmulatorInstance extends EmulatorInstance {

    private static final Logger LOG = LoggerFactory.getLogger(AndroidSdkEmulatorInstance.class);
    private static final int FIRST_CONSOLE_PORT = 5554;
    private static final int PORT_STRIDE = 2;
    private static final Duration ACTION_TIMEOUT = Duration.ofSeconds(15);

    public AndroidSdkEmulatorInstance(String adbPath) {
        super("", adbPath);
    }

    @Override
    protected String getDeviceSerial(String identifier) {
        return resolveSerial(identifier);
    }

    static String resolveSerial(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty()) {
            value = "0";
        }
        if (value.startsWith("emulator-") || value.contains(":")) {
            return value;
        }
        int number = Integer.parseInt(value);
        if (number >= FIRST_CONSOLE_PORT && number % 2 == 0) {
            return "emulator-" + number;
        }
        return "emulator-" + (FIRST_CONSOLE_PORT + number * PORT_STRIDE);
    }

    @Override
    public void launchEmulator(String identifier) {
        if (isRunning(identifier)) {
            return;
        }
        LOG.warn("Android emulator {} is offline; start it in your emulator manager before running Frostguard",
                getDeviceSerial(identifier));
    }

    @Override
    public void closeEmulator(String identifier) {
        String serial = getDeviceSerial(identifier);
        try {
            BoundedProcessRunner.ProcessResult result = BoundedProcessRunner.run(
                    new ProcessBuilder(getAdbPath(), "-s", serial, "emu", "kill"), ACTION_TIMEOUT);
            if (result.timedOut()) {
                LOG.warn("Timed out while stopping Android emulator {}", serial);
            } else if (result.exitCode() != 0) {
                LOG.warn("Could not stop Android emulator {}: {}", serial, result.output().trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOG.warn("Could not stop Android emulator {}: {}", serial, e.getMessage());
        }
        invalidateAllCaches(identifier);
    }

    @Override
    public boolean isRunning(String identifier) {
        try {
            IDevice device = getCachedDevice(identifier);
            return device != null && device.isOnline();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            LOG.debug("Android emulator {} is not available: {}", getDeviceSerial(identifier), e.getMessage());
            return false;
        }
    }
}
