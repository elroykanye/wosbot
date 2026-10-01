package dev.frostguard.engine.emulator;

import dev.frostguard.api.domain.PointData;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts safe tap targets from Android's system-owned UI hierarchy. */
final class AndroidSystemDialogParser {

    private static final Pattern NODE = Pattern.compile("<node\\b[^>]*>");
    private static final Pattern RESOURCE_ID = Pattern.compile("resource-id=\"([^\"]*)\"");
    private static final Pattern BOUNDS = Pattern.compile(
            "bounds=\"\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]\"");

    private AndroidSystemDialogParser() {}

    static Optional<PointData> buttonCenter(String hierarchy, String resourceName) {
        if (hierarchy == null || hierarchy.isBlank() || resourceName == null || resourceName.isBlank()) {
            return Optional.empty();
        }
        Matcher nodes = NODE.matcher(hierarchy);
        while (nodes.find()) {
            String node = nodes.group();
            Matcher resource = RESOURCE_ID.matcher(node);
            if (!resource.find() || !resource.group(1).endsWith("/" + resourceName)) {
                continue;
            }
            Matcher bounds = BOUNDS.matcher(node);
            if (!bounds.find()) {
                return Optional.empty();
            }
            int left = Integer.parseInt(bounds.group(1));
            int top = Integer.parseInt(bounds.group(2));
            int right = Integer.parseInt(bounds.group(3));
            int bottom = Integer.parseInt(bounds.group(4));
            if (right <= left || bottom <= top) {
                return Optional.empty();
            }
            return Optional.of(new PointData((left + right) / 2, (top + bottom) / 2));
        }
        return Optional.empty();
    }
}
