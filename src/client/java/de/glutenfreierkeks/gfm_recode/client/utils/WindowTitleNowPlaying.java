package de.glutenfreierkeks.gfm_recode.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Reads song title + artist from media app window titles (Spotify, browsers, etc.).
 */
public final class WindowTitleNowPlaying {
    private static final Pattern SPOTIFY_SUFFIX = Pattern.compile(
            "\\s*[-–—|]\\s*Spotify(?:\\s+Premium)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_SUFFIX = Pattern.compile(
            "\\s*[-–—|]\\s*YouTube(?:\\s+Music)?\\s*$", Pattern.CASE_INSENSITIVE);

    /** Fast: only queries known process names, not every process on the system. */
    private static final String PS_SCRIPT = """
            $names = @('Spotify','msedge','chrome','firefox','brave','opera')
            foreach ($n in $names) {
                $p = Get-Process -Name $n -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1
                if (-not $p) { continue }
                $t = $p.MainWindowTitle.Trim()
                if ($t.Length -lt 4) { continue }
                if ($t -eq 'Spotify' -or $t -eq 'Spotify Premium') { continue }
                Write-Output ('{"available":true,"process":"' + $p.ProcessName + '","title":"' + ($t -replace '"','\\"') + '"}')
                exit
            }
            Write-Output '{"available":false,"reason":"Kein Media-Fenster"}'
            """;

    private WindowTitleNowPlaying() {}

    public static Map<String, Object> fetchBlocking() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", PS_SCRIPT
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }
            }

            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return unavailable("Timeout");
            }

            String raw = out.toString().trim();
            if (raw.isEmpty() || !raw.startsWith("{")) {
                return unavailable("Kein Fenster-Titel");
            }

            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            if (!json.has("available") || !json.get("available").getAsBoolean()) {
                return unavailable(json.has("reason") ? json.get("reason").getAsString() : "Nicht verfügbar");
            }

            String windowTitle = json.get("title").getAsString();
            String processName = json.has("process") ? json.get("process").getAsString() : "";

            Parsed parsed = parse(windowTitle, processName);
            if (parsed.title.isBlank()) {
                return unavailable("Titel leer");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("available", true);
            data.put("connected", true);
            data.put("title", parsed.title);
            data.put("artist", parsed.artist);
            data.put("source", parsed.source);
            data.put("status", "Playing");
            data.put("albumArt", "");
            return data;
        } catch (Exception e) {
            Gfm_recodeClient.LOG.debug("Window title now playing failed", e);
            return unavailable("Fenster lesen fehlgeschlagen");
        }
    }

    private static Parsed parse(String windowTitle, String processName) {
        String title = windowTitle.trim();
        String proc = processName.toLowerCase(Locale.ROOT);

        if (proc.contains("spotify")) {
            title = SPOTIFY_SUFFIX.matcher(title).replaceAll("").trim();
            return parseSpotifyTrack(title);
        }

        title = YOUTUBE_SUFFIX.matcher(title).replaceAll("").trim();
        if (proc.contains("chrome") || proc.contains("edge") || proc.contains("firefox")
                || proc.contains("brave") || proc.contains("opera") || proc.contains("vivaldi")) {
            return parseBrowserTitle(title);
        }

        return new Parsed(title, "", "Media");
    }

    private static Parsed parseSpotifyTrack(String title) {
        String[] separators = {" – ", " - ", " — ", " · ", " | "};
        for (String sep : separators) {
            int idx = title.indexOf(sep);
            if (idx > 0) {
                String track = title.substring(0, idx).trim();
                String artist = title.substring(idx + sep.length()).trim();
                if (!track.isBlank()) {
                    return new Parsed(track, artist, "Spotify");
                }
            }
        }
        if (title.equalsIgnoreCase("Spotify") || title.isBlank()) {
            return new Parsed("", "", "Spotify");
        }
        return new Parsed(title, "", "Spotify");
    }

    private static Parsed parseBrowserTitle(String title) {
        String[] parts = title.split(" [-–—|] ");
        if (parts.length >= 2) {
            return new Parsed(parts[0].trim(), parts[1].trim(), "Browser");
        }
        return new Parsed(title, "", "Browser");
    }

    private static Map<String, Object> unavailable(String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("connected", true);
        data.put("reason", reason);
        return data;
    }

    private record Parsed(String title, String artist, String source) {
        Parsed(String title, String artist, String source) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.source = source == null ? "Media" : source;
        }
    }
}
