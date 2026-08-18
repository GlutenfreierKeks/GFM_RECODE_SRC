package de.glutenfreierkeks.gfm_recode.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaSessionUtil {

    private static final long POLL_INTERVAL_MS = 2000L;
    private static final AtomicBoolean POLLING = new AtomicBoolean(false);
    private static volatile long lastPoll = 0;
    private static volatile Map<String, Object> cached = unavailable("Not polled yet");

    // Inline C# kompiliert und ausgeführt via PowerShell Add-Type
    // Läuft im STA-Thread → WinRT funktioniert zuverlässig
    private static final String PS_SCRIPT = """
            $code = @'
            using System;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.WindowsRuntime;
            using System.Threading;
            using Windows.Media.Control;

            public class MediaQuery {
                [DllImport("ole32.dll")]
                static extern int CoInitializeEx(IntPtr pvReserved, uint dwCoInit);

                public static string Query() {
                    CoInitializeEx(IntPtr.Zero, 0x2); // STA
                    try {
                        var task = GlobalSystemMediaTransportControlsSessionManager.RequestAsync().AsTask();
                        task.Wait(3000);
                        if (!task.IsCompletedSuccessfully) return @"{""available"":false,""reason"":""Manager timeout""}";

                        var manager = task.Result;
                        var session = manager.GetCurrentSession();
                        if (session == null) return @"{""available"":false,""reason"":""No active media session""}";

                        var propsTask = session.TryGetMediaPropertiesAsync().AsTask();
                        propsTask.Wait(2000);
                        if (!propsTask.IsCompletedSuccessfully) return @"{""available"":false,""reason"":""Props timeout""}";

                        var props = propsTask.Result;
                        var timeline = session.GetTimelineProperties();
                        var playback = session.GetPlaybackInfo();
                        string source = "";
                        try { source = session.SourceAppUserModelId; } catch {}

                        string title = props.Title ?? "";
                        string artist = props.Artist ?? "";
                        int position = (int)Math.Max(0, timeline.Position.TotalSeconds);
                        int duration = (int)Math.Max(0, timeline.EndTime.TotalSeconds);
                        string status = playback.PlaybackStatus.ToString();

                        return $@"{{""available"":true,""title"":""{Escape(title)}"",""artist"":""{Escape(artist)}"",""source"":""{Escape(source)}"",""status"":""{status}"",""position"":{position},""duration"":{duration}}}";
                    } catch (Exception ex) {
                        return $@"{{""available"":false,""reason"":""{Escape(ex.Message)}""}}";
                    }
                }

                static string Escape(string s) =>
                    s?.Replace("\\\\", "\\\\\\\\").Replace("\\"", "\\\\\\"").Replace("\\r", "").Replace("\\n", "") ?? "";
            }
'@
            try {
                Add-Type -TypeDefinition $code -Language CSharp -ReferencedAssemblies @(
                    'System.Runtime.InteropServices.WindowsRuntime',
                    [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions].Assembly.Location,
                    (Resolve-Path "$env:SystemRoot\\System32\\WinMetadata\\Windows.Media.winmd").Path
                ) -ErrorAction Stop
            } catch {
                Write-Output "{\\"available\\":false,\\"reason\\":\\"Compile error: $($_.Exception.Message)\\"}"
                exit
            }
            Write-Output ([MediaQuery]::Query())
            """;

    private MediaSessionUtil() {}

    public static Map<String, Object> getCurrent() {
        long now = System.currentTimeMillis();
        if (now - lastPoll >= POLL_INTERVAL_MS && POLLING.compareAndSet(false, true)) {
            lastPoll = now;
            CompletableFuture.runAsync(MediaSessionUtil::pollNow);
        }
        return cached;
    }

    /** Synchronous read of the active Windows media session. */
    public static Map<String, Object> fetchNowBlocking() {
        return tryPowerShellCSharp();
    }

    private static void pollNow() {
        try {
            cached = tryPowerShellCSharp();
        } finally {
            POLLING.set(false);
        }
    }

    private static Map<String, Object> tryPowerShellCSharp() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", PS_SCRIPT
            );
            pb.redirectErrorStream(false); // stderr separat lesen
            Process process = pb.start();

            // stderr in separatem Thread lesen damit kein Deadlock entsteht
            StringBuilder stderr = new StringBuilder();
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) stderr.append(line).append('\n');
                } catch (Exception ignored) {}
            });
            stderrThread.setDaemon(true);
            stderrThread.start();

            // stdout lesen
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) stdout.append(line);
            }

            boolean finished = process.waitFor(8000L, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                Gfm_recodeClient.LOG.warn("Media session PS timeout");
                return unavailable("PowerShell timeout");
            }

            String output = stdout.toString().trim();
            Gfm_recodeClient.LOG.debug("Media PS stdout: {}", output);
            Gfm_recodeClient.LOG.debug("Media PS stderr: {}", stderr);

            if (output.isEmpty()) {
                return unavailable("No output from PowerShell");
            }

            // Manchmal gibt PS mehrere Zeilen aus; letzte JSON-Zeile nehmen
            String jsonLine = output;
            for (String part : output.split("\n")) {
                part = part.trim();
                if (part.startsWith("{")) jsonLine = part;
            }

            return parseJson(jsonLine);

        } catch (Exception e) {
            Gfm_recodeClient.LOG.debug("Media session failed", e);
            return unavailable("Exception: " + e.getMessage());
        }
    }

    private static Map<String, Object> parseJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            boolean available = obj.has("available") && obj.get("available").getAsBoolean();

            if (!available) {
                return unavailable(getString(obj, "reason", "No session"));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("available", true);
            data.put("title",    getString(obj, "title",  "Unknown Track"));
            data.put("artist",   getString(obj, "artist", ""));
            data.put("source",   cleanSource(getString(obj, "source", "Media")));
            data.put("status",   getString(obj, "status", "Unknown"));
            data.put("position", getInt(obj, "position"));
            data.put("duration", getInt(obj, "duration"));
            return data;

        } catch (Exception e) {
            Gfm_recodeClient.LOG.warn("Media JSON parse error: {} | input: {}", e.getMessage(), json);
            return unavailable("JSON parse error");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String getString(JsonObject obj, String key, String fallback) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        String v = obj.get(key).getAsString();
        return v == null || v.isBlank() ? fallback : v;
    }

    private static int getInt(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsInt() : 0; }
        catch (Exception e) { return 0; }
    }

    private static String cleanSource(String source) {
        String l = source.toLowerCase();
        if (l.contains("spotify"))                                          return "Spotify";
        if (l.contains("youtube") || l.contains("chrome")
                || l.contains("msedge") || l.contains("firefox"))          return "YouTube / Browser";
        if (l.contains("amazon"))                                           return "Amazon Music";
        return source;
    }

    private static Map<String, Object> unavailable(String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("reason", reason);
        return data;
    }
}