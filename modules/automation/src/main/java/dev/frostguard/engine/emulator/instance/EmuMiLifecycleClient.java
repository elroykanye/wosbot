package dev.frostguard.engine.emulator.instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Local lifecycle bridge to the EmuMi desktop manager. */
final class EmuMiLifecycleClient {

    private static final Logger LOG = LoggerFactory.getLogger(EmuMiLifecycleClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    boolean start(int adbPort) {
        return post(adbPort, "start");
    }

    boolean stop(int adbPort) {
        return post(adbPort, "stop");
    }

    private boolean post(int adbPort, String action) {
        Optional<URI> endpoint = discoverEndpoint();
        if (endpoint.isEmpty()) {
            LOG.warn("EmuMi endpoint is unavailable; open EmuMi before Frostguard needs device {}", adbPort);
            return false;
        }
        URI uri = endpoint.get().resolve("/api/ports/" + adbPort + "/" + action);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            LOG.warn("EmuMi rejected {} for ADB {} (HTTP {}): {}", action, adbPort,
                    response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalArgumentException failure) {
            LOG.warn("Could not ask EmuMi to {} ADB {}: {}", action, adbPort, failure.getMessage());
        }
        return false;
    }

    static Optional<URI> discoverEndpoint() {
        String configured = System.getenv("EMUMI_URL");
        if (configured == null || configured.isBlank()) {
            Path stateRoot;
            String xdgState = System.getenv("XDG_STATE_HOME");
            if (xdgState == null || xdgState.isBlank()) {
                stateRoot = Path.of(System.getProperty("user.home"), ".local", "state");
            } else {
                stateRoot = Path.of(xdgState);
            }
            try {
                configured = Files.readString(stateRoot.resolve("emumi").resolve("api-url")).trim();
            } catch (IOException ignored) {
                return Optional.empty();
            }
        }
        try {
            URI endpoint = URI.create(configured.endsWith("/") ? configured : configured + "/");
            String host = endpoint.getHost();
            if (!"http".equalsIgnoreCase(endpoint.getScheme())
                    || !("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))) {
                LOG.warn("Ignoring non-local EmuMi endpoint {}", endpoint);
                return Optional.empty();
            }
            return Optional.of(endpoint);
        } catch (IllegalArgumentException invalid) {
            LOG.warn("Ignoring invalid EmuMi endpoint: {}", invalid.getMessage());
            return Optional.empty();
        }
    }
}
