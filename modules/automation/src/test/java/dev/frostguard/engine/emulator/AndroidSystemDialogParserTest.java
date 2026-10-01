package dev.frostguard.engine.emulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostguard.api.domain.PointData;
import org.junit.jupiter.api.Test;

class AndroidSystemDialogParserTest {

    @Test
    void locatesAndroidAnrWaitButtonWithoutDependingOnScreenCoordinates() {
        String hierarchy = """
                <hierarchy>
                  <node text="Close app" resource-id="android:id/aerr_close"
                        bounds="[96,590][624,682]" />
                  <node text="Wait" resource-id="android:id/aerr_wait"
                        bounds="[96,682][624,774]" />
                </hierarchy>
                """;

        assertEquals(new PointData(360, 728),
                AndroidSystemDialogParser.buttonCenter(hierarchy, "aerr_wait").orElseThrow());
    }

    @Test
    void refusesUnrelatedOrMalformedNodes() {
        assertTrue(AndroidSystemDialogParser.buttonCenter(
                "<node text=\"Wait\" resource-id=\"app:id/wait\" bounds=\"[0,0][10,10]\" />",
                "aerr_wait").isEmpty());
        assertTrue(AndroidSystemDialogParser.buttonCenter(
                "<node resource-id=\"android:id/aerr_wait\" bounds=\"[2,2][2,8]\" />",
                "aerr_wait").isEmpty());
    }
}
