package com.angelonmx.megaequalizer.coverage;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the playback section of {@code dumpsys audio}. ADB and root both
 * return the same text, so a single parser keeps both backends consistent.
 */
public final class AudioSessionDumpParser {

    private static final Pattern PID_PATTERN = Pattern.compile("u/pid:(\\d+)/(\\d+)");
    private static final Pattern SESSION_PATTERN = Pattern.compile("sessionId:(\\d+)");
    private static final Pattern STATE_PATTERN = Pattern.compile("state:([a-z]+)");
    private static final Pattern USAGE_PATTERN = Pattern.compile("usage=(USAGE_[A-Z0-9_]+)");
    private static final Pattern CONTENT_PATTERN = Pattern.compile("content=(CONTENT_TYPE_[A-Z0-9_]+)");

    private AudioSessionDumpParser() {
    }

    /**
     * @param dump     output of {@code dumpsys audio}
     * @param manager  package manager used to turn UIDs into package names
     * @param ownUid   sessions owned by this app are ignored
     */
    @NonNull
    public static List<AudioSessionInfo> parse(
            String dump,
            PackageManager manager,
            int ownUid
    ) {
        List<AudioSessionInfo> result = new ArrayList<>();
        if (dump == null || dump.isEmpty()) {
            return result;
        }

        Map<Integer, AudioSessionInfo> bySession = new LinkedHashMap<>();
        String[] records = dump.split("AudioPlaybackConfiguration");
        for (int i = 1; i < records.length; i++) {
            String record = records[i];
            Matcher sessionMatcher = SESSION_PATTERN.matcher(record);
            Matcher pidMatcher = PID_PATTERN.matcher(record);
            Matcher stateMatcher = STATE_PATTERN.matcher(record);
            if (!sessionMatcher.find() || !pidMatcher.find() || !stateMatcher.find()) {
                continue;
            }
            int sessionId;
            int uid;
            try {
                sessionId = Integer.parseInt(sessionMatcher.group(1));
                uid = Integer.parseInt(pidMatcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (sessionId <= 0 || uid == ownUid) {
                continue;
            }
            String state = stateMatcher.group(1);
            if (!"started".equals(state) && !"paused".equals(state)) {
                continue;
            }
            String usage = match(USAGE_PATTERN, record, "USAGE_UNKNOWN");
            String contentType = match(CONTENT_PATTERN, record, "CONTENT_TYPE_UNKNOWN");
            int pid;
            try {
                pid = Integer.parseInt(pidMatcher.group(2));
            } catch (NumberFormatException ignored) {
                pid = 0;
            }
            AudioSessionInfo info = new AudioSessionInfo(
                    sessionId,
                    uid,
                    pid,
                    state,
                    usage,
                    contentType,
                    resolvePackage(manager, uid)
            );
            AudioSessionInfo previous = bySession.get(sessionId);
            if (previous == null || (!previous.isActive() && info.isActive())) {
                bySession.put(sessionId, info);
            }
        }
        result.addAll(bySession.values());
        return result;
    }

    private static String match(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    @NonNull
    private static String resolvePackage(PackageManager manager, int uid) {
        if (manager == null) {
            return "";
        }
        try {
            String name = manager.getNameForUid(uid);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            String[] packages = manager.getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return packages[0] == null ? "" : packages[0];
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }
}
