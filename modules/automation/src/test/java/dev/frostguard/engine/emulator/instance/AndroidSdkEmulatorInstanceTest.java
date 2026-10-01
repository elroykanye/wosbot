package dev.frostguard.engine.emulator.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AndroidSdkEmulatorInstanceTest {

    @Test
    void mapsProfileSlotsToAndroidEmulatorConsoleSerials() {
        assertEquals("emulator-5554", AndroidSdkEmulatorInstance.resolveSerial("0"));
        assertEquals("emulator-5554", AndroidSdkEmulatorInstance.resolveSerial(""));
        assertEquals("emulator-5554", AndroidSdkEmulatorInstance.resolveSerial(null));
        assertEquals("emulator-5556", AndroidSdkEmulatorInstance.resolveSerial("1"));
        assertEquals("emulator-5554", AndroidSdkEmulatorInstance.resolveSerial("5554"));
        assertEquals("emulator-5554", AndroidSdkEmulatorInstance.resolveSerial("emulator-5554"));
        assertEquals("127.0.0.1:5555", AndroidSdkEmulatorInstance.resolveSerial("127.0.0.1:5555"));
    }

    @Test
    void mapsProfileSlotsToEmuMiConsolePorts() {
        assertEquals(5554, AndroidSdkEmulatorInstance.resolveConsolePort("0"));
        assertEquals(5556, AndroidSdkEmulatorInstance.resolveConsolePort("1"));
        assertEquals(5558, AndroidSdkEmulatorInstance.resolveConsolePort("emulator-5558"));
    }
}
