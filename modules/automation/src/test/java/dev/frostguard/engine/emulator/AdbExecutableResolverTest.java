package dev.frostguard.engine.emulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdbExecutableResolverTest {

    @TempDir
    Path temp;

    @Test
    void macUsesBundledAdbBeforeTheSystemPath() throws Exception {
        Path bundled = temp.resolve("packaging/desktop/target/input/lib/adb/adb");
        Files.createDirectories(bundled.getParent());
        Files.createFile(bundled);
        Path system = temp.resolve("bin/adb");
        Files.createDirectories(system.getParent());
        Files.createFile(system);

        assertEquals(bundled.toAbsolutePath().toString(), AdbExecutableResolver.resolve(
                "Mac OS X", temp, "", "", system.getParent().toString()));
    }

    @Test
    void macFallsBackToAdbFoundOnPath() throws Exception {
        Path system = temp.resolve("bin/adb");
        Files.createDirectories(system.getParent());
        Files.createFile(system);

        assertEquals(system.toAbsolutePath().toString(), AdbExecutableResolver.resolve(
                "Mac OS X", temp, "", "", system.getParent().toString()));
    }

    @Test
    void explicitOverrideWinsOnEveryPlatform() throws Exception {
        Path explicit = Files.createFile(temp.resolve("custom-adb"));

        assertEquals(explicit.toAbsolutePath().toString(), AdbExecutableResolver.resolve(
                "Mac OS X", temp, "", explicit.toString(), ""));
    }

    @Test
    void windowsRetainsEmulatorConsoleFallback() {
        Path console = temp.resolve("emulator");

        assertEquals(console.resolve("adb.exe").toString(), AdbExecutableResolver.resolve(
                "Windows 11", temp, console.toString(), "", ""));
    }
}
