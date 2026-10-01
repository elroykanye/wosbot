package dev.frostguard.engine.emulator.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class MuMuMacEmulatorInstanceTest {

    @Test
    void readsTheConfiguredAdbPortFromMuMuInfo() {
        String info = "{\"vmName\":\"Melon\",\"customAdbPort\":26624}";

        assertEquals("127.0.0.1:26624", MuMuMacEmulatorInstance.serialFromInfo("0", info));
    }

    @Test
    void fallsBackToMuMusDefaultPortSequence() {
        assertEquals("127.0.0.1:16384", MuMuMacEmulatorInstance.serialFromInfo("0", ""));
        assertEquals("127.0.0.1:16416", MuMuMacEmulatorInstance.serialFromInfo("1", "bad output"));
    }

    @Test
    void usesTheDocumentedLifecycleCommands() {
        assertEquals(List.of("/tmp/mumutool", "open", "2"),
                MuMuMacEmulatorInstance.command("/tmp/mumutool", "open", "2"));
        assertEquals(List.of("/tmp/mumutool", "close", "2"),
                MuMuMacEmulatorInstance.command("/tmp/mumutool", "close", "2"));
    }
}
