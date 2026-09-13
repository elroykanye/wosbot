package dev.frostguard.engine.emulator.instance;

import com.android.ddmlib.IDevice;
import dev.frostguard.engine.emulator.BoundedProcessRunner;
import dev.frostguard.engine.emulator.EmulatorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Controls Apple Silicon MuMuPlayer devices through its documented {@code mumutool} CLI. */
public final class MuMuMacEmulatorInstance extends EmulatorInstance {

    private static final Logger LOG = LoggerFactory.getLogger(MuMuMacEmulatorInstance.class);
    private static final Pattern ADB_PORT = Pattern.compile("\\\"customAdbPort\\\"\\s*:\\s*(\\d+)");
    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(10);
    private final String tool;
    private final Map<String, String> serials = new ConcurrentHashMap<>();

    public MuMuMacEmulatorInstance(String configuredPath) {
        super("");
        this.tool = resolveTool(configuredPath);
    }

    @Override
    protected String getDeviceSerial(String identifier) {
        return serials.computeIfAbsent(identifier, this::readDeviceSerial);
    }

    private String readDeviceSerial(String identifier) {
        try {
            BoundedProcessRunner.ProcessResult result = BoundedProcessRunner.run(
                    process("info", identifier), TOOL_TIMEOUT);
            if (!result.timedOut() && result.exitCode() == 0) {
                return serialFromInfo(identifier, result.output());
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            LOG.warn("Could not read MuMuPlayer device {} info: {}", identifier, failure.getMessage());
        }
        return serialFromInfo(identifier, "");
    }

    static String serialFromInfo(String identifier, String output) {
        int index;
        try {
            index = Integer.parseInt(identifier);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("MuMuPlayer device number must be an integer", failure);
        }
        if (index < 0) {
            throw new IllegalArgumentException("MuMuPlayer device number cannot be negative");
        }
        Matcher matcher = ADB_PORT.matcher(output == null ? "" : output);
        int port = matcher.find() ? Integer.parseInt(matcher.group(1)) : 16384 + index * 32;
        return "127.0.0.1:" + port;
    }

    @Override
    public void launchEmulator(String identifier) {
        runLifecycle("open", identifier);
    }

    @Override
    public void closeEmulator(String identifier) {
        runLifecycle("close", identifier);
        invalidateAllCaches(identifier);
        serials.remove(identifier);
    }

    @Override
    public boolean isRunning(String identifier) {
        try {
            IDevice device = getCachedDevice(identifier);
            return device != null && device.isOnline();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void runLifecycle(String action, String identifier) {
        try {
            BoundedProcessRunner.ProcessResult result = BoundedProcessRunner.run(
                    process(action, identifier), TOOL_TIMEOUT);
            if (result.timedOut()) {
                LOG.error("MuMuPlayer {} timed out for device {}", action, identifier);
            } else if (result.exitCode() != 0) {
                LOG.error("MuMuPlayer {} failed for device {} with exit code {}: {}",
                        action, identifier, result.exitCode(), result.output());
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            LOG.error("Could not run MuMuPlayer {} for device {}", action, identifier, failure);
        }
    }

    private ProcessBuilder process(String action, String identifier) {
        ProcessBuilder builder = new ProcessBuilder(command(tool, action, identifier));
        File executable = new File(tool);
        if (executable.isAbsolute() && executable.getParentFile() != null) {
            builder.directory(executable.getParentFile());
        }
        return builder;
    }

    static List<String> command(String tool, String action, String identifier) {
        return List.of(tool, action, identifier);
    }

    private static String resolveTool(String configuredPath) {
        List<Path> candidates = new ArrayList<>();
        addCandidate(candidates, configuredPath);
        addCandidate(candidates, System.getProperty("frostguard.mumutool.path", ""));
        addCandidate(candidates, System.getenv().getOrDefault("FROSTGUARD_MUMUTOOL_PATH", ""));

        String path = System.getenv().getOrDefault("PATH", "");
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                candidates.add(Path.of(entry).resolve("mumutool"));
            }
        }
        candidates.add(Path.of("/Applications/MuMuPlayer.app/Contents/MacOS/mumutool"));
        candidates.add(Path.of("/Applications/MuMuPlayer Pro.app/Contents/MacOS/mumutool"));
        candidates.add(Path.of(System.getProperty("user.home"),
                "Applications/MuMuPlayer.app/Contents/MacOS/mumutool"));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        return "mumutool";
    }

    private static void addCandidate(List<Path> candidates, String value) {
        if (value != null && !value.isBlank()) {
            Path candidate = Path.of(value);
            candidates.add(Files.isDirectory(candidate) ? candidate.resolve("mumutool") : candidate);
        }
    }
}
