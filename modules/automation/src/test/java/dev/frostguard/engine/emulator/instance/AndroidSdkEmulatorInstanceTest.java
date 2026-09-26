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
}
