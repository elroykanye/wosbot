package dev.frostguard.engine.emulator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmulatorTypeTest {

    @Test
    void mumuMacDoesNotRequireAWindowsManagerPath() {
        assertFalse(EmulatorType.MUMU_MAC.requiresExecutablePath());
        assertTrue(EmulatorType.MUMU.requiresExecutablePath());
    }

    @Test
    void exposesOnlyBackendsSupportedByTheHost() {
        assertTrue(EmulatorType.MUMU_MAC.supports("Mac OS X", "aarch64"));
        assertFalse(EmulatorType.MUMU_MAC.supports("Mac OS X", "x86_64"));
        assertFalse(EmulatorType.MUMU.supports("Mac OS X", "aarch64"));
        assertFalse(EmulatorType.MUMU_MAC.supports("Mac OS X", "x86_64"));
    }
}
