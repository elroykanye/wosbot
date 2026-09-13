package dev.frostguard.engine.emulator;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import java.nio.file.Path;
import java.nio.file.Paths;

// Supported emulator backends with their CLI binary metadata.
public enum EmulatorType {

    MUMU("MuMuPlayer", ConfigurationKeyEnum.MUMU_PATH_STRING, "MuMuManager.exe",
            "C:\\Program Files\\Netease\\MuMuPlayer\\nx_main"),

    MEMU("MEmu Player", ConfigurationKeyEnum.MEMU_PATH_STRING, "memuc.exe",
            "C:\\Program Files\\Microvirt\\MEmu"),

    LDPLAYER("LDPlayer", ConfigurationKeyEnum.LDPLAYER_PATH_STRING, "ldconsole.exe",
            "C:\\LDPlayer\\LDPlayer9"),

    MUMU_MAC("MuMuPlayer for Mac", ConfigurationKeyEnum.MUMU_MAC_PATH_STRING,
            "mumutool", "", false);

    private final String label;
    private final ConfigurationKeyEnum cfgKey;
    private final String exe;
    private final String fallbackDir;
    private final boolean requiresExecutablePath;

    EmulatorType(String label, ConfigurationKeyEnum cfgKey, String exe, String fallbackDir) {
        this(label, cfgKey, exe, fallbackDir, true);
    }

    EmulatorType(String label, ConfigurationKeyEnum cfgKey, String exe, String fallbackDir,
            boolean requiresExecutablePath) {
        this.label = label;
        this.cfgKey = cfgKey;
        this.exe = exe;
        this.fallbackDir = fallbackDir;
        this.requiresExecutablePath = requiresExecutablePath;
    }

    public String getDisplayName()   { return label; }
    public String getConfigKey()     { return cfgKey.name(); }
    public ConfigurationKeyEnum getConfigEnum() { return cfgKey; }
    public String getExecutableName(){ return exe; }
    public boolean requiresExecutablePath() { return requiresExecutablePath; }

    public boolean supports(String operatingSystem, String architecture) {
        String os = operatingSystem == null ? "" : operatingSystem.toLowerCase(java.util.Locale.ROOT);
        String arch = architecture == null ? "" : architecture.toLowerCase(java.util.Locale.ROOT);
        boolean windows = os.contains("win");
        boolean mac = os.contains("mac");
        return switch (this) {
            case MUMU, MEMU, LDPLAYER -> windows;
            case MUMU_MAC -> mac && (arch.contains("aarch64") || arch.contains("arm64"));
        };
    }

    public boolean supportsCurrentPlatform() {
        return supports(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public String getDefaultPath() {
        return requiresExecutablePath ? Paths.get(fallbackDir, exe).toString() : "";
    }

    public String resolvePath(String override) {
        return (override == null || override.isBlank())
                ? getDefaultPath()
                : Paths.get(override, exe).toString();
    }
}
