package dev.frostguard.engine.emulator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AdbExecutableResolver {

    private static final String ADB_PATH_PROPERTY = "frostguard.adb.path";
    private static final String ADB_PATH_ENVIRONMENT = "FROSTGUARD_ADB_PATH";

    private AdbExecutableResolver() {}

    static String resolveCurrent(String consolePath) {
        String override = System.getProperty(ADB_PATH_PROPERTY, "").trim();
        if (override.isBlank()) {
            override = System.getenv().getOrDefault(ADB_PATH_ENVIRONMENT, "").trim();
        }
        if (override.isBlank()) {
            override = detectAndroidSdkAdb();
        }
        return resolve(System.getProperty("os.name", ""), Path.of(System.getProperty("user.dir")),
                consolePath, override, System.getenv().getOrDefault("PATH", ""));
    }

    private static String detectAndroidSdkAdb() {
        List<String> sdkRoots = List.of(
                System.getenv().getOrDefault("ANDROID_SDK_ROOT", ""),
                System.getenv().getOrDefault("ANDROID_HOME", ""),
                Path.of(System.getProperty("user.home"), "Android", "Sdk").toString());
        for (String root : sdkRoots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path candidate = Path.of(root, "platform-tools", "adb");
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return "";
    }

    static String resolve(String osName, Path workingDirectory, String consolePath,
            String override, String pathEnvironment) {
        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        String executable = windows ? "adb.exe" : "adb";

        List<Path> candidates = new ArrayList<>();
        addCandidate(candidates, override);
        candidates.add(workingDirectory.resolve("lib/adb").resolve(executable));
        candidates.add(workingDirectory.resolve("tools/adb").resolve(executable));
        candidates.add(workingDirectory.resolve("packaging/desktop/target/input/lib/adb")
                .resolve(executable));
        if (pathEnvironment != null && !pathEnvironment.isBlank()) {
            for (String entry : pathEnvironment.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!entry.isBlank()) {
                    candidates.add(Path.of(entry).resolve(executable));
                }
            }
        }

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }

        if (windows && consolePath != null && !consolePath.isBlank()) {
            return Path.of(consolePath).resolve(executable).toString();
        }
        return executable;
    }

    private static void addCandidate(List<Path> candidates, String rawPath) {
        if (rawPath != null && !rawPath.isBlank()) {
            candidates.add(Path.of(rawPath));
        }
    }
}
