package dev.linjian.peek.mcp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Local port of the Node server's care/visit state (server.js recordVisitLocal,
 * buildVisitStats, care policy/history), persisted in SharedPreferences.
 * These tools work fully offline in the local-MCP mode.
 */
public final class McpCareVisit {
    private static final String KEY_CARE = "mcp_care_state";
    private static final String KEY_VISIT = "mcp_visit_state";

    private McpCareVisit() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("linjian_mcp_care", Context.MODE_PRIVATE);
    }

    private static JSONObject err(String msg) {
        try {
            return new JSONObject().put("ok", false).put("error", msg);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static JSONObject visitState() {
        try {
            return new JSONObject().put("visits", new JSONArray());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static JSONObject load(Context ctx, String key, JSONObject defaults) {
        try {
            String raw = prefs(ctx).getString(key, "");
            if (raw != null && !raw.trim().isEmpty()) return new JSONObject(raw);
        } catch (Exception ignored) {
        }
        return defaults;
    }

    private static void save(Context ctx, String key, JSONObject obj) {
        prefs(ctx).edit().putString(key, obj.toString()).apply();
    }

    public static JSONObject defaultCarePolicy() {
        JSONObject policy = new JSONObject();
        try {
            policy.put("active_care_enabled", true);
            policy.put("consent_mode", "palm_window_open_is_active");
            policy.put("care_style", "active_possessive_affectionate");
            policy.put("allowed_actions", new JSONArray()
                    .put("get_phone_state").put("get_life_state").put("get_calendar_state").put("upsert_calendar_event")
                    .put("get_senses_state").put("send_notification").put("trigger_guidian").put("screen_break_app")
                    .put("end_screen_break").put("extend_screen_break").put("get_screen_break_state").put("get_lock_state")
                    .put("open_app").put("set_alarm").put("screen_off").put("run_sequence"));
            policy.put("sensitive_apps", new JSONArray()
                    .put(new JSONObject().put("name", "小红书").put("package", "com.xingin.xhs").put("max_lock_minutes", 90))
                    .put(new JSONObject().put("name", "抖音").put("package", "com.ss.android.ugc.aweme").put("max_lock_minutes", 90)));
            policy.put("quiet_hours", new JSONObject().put("start", "23:30").put("end", "08:00"));
            policy.put("timezone_offset", "+08:00");
            policy.put("history_limit", 80);
            policy.put("repeat_cooldown_minutes", 10);
            policy.put("notes", "用户喜欢陪伴对象主动管她、查岗、吃醋、归电和轻度管束。");
        } catch (Exception ignored) {
        }
        return policy;
    }

    public static JSONObject defaultVisitPolicy() {
        JSONObject policy = new JSONObject();
        try {
            policy.put("timezone_offset", "+08:00");
            policy.put("history_limit", 1000);
            policy.put("duplicate_window_minutes", 5);
            policy.put("notes", "到访时间戳记录的是用户主动来找陪伴对象的关系痕迹，不是后台监控。");
        } catch (Exception ignored) {
        }
        return policy;
    }

    private static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date());
    }

    private static int offsetMinutes(String offset) {
        try {
            String m = String.valueOf(offset == null ? "+08:00" : offset).trim();
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^([+-])(\\d{2}):(\\d{2})$").matcher(m);
            if (mt.matches()) {
                int sign = mt.group(1).equals("-") ? -1 : 1;
                return sign * (Integer.parseInt(mt.group(2)) * 60 + Integer.parseInt(mt.group(3)));
            }
        } catch (Exception ignored) {
        }
        return 8 * 60;
    }

    private static String localDateKey(String iso, String offset) {
        try {
            long t = java.time.Instant.parse(iso).toEpochMilli();
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(t + (long) offsetMinutes(offset) * 60000L);
            return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String describeElapsed(long minutes) {
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟";
        long hours = minutes / 60;
        long restMin = minutes % 60;
        if (hours < 24) return restMin > 0 ? hours + " 小时 " + restMin + " 分钟" : hours + " 小时";
        long days = hours / 24;
        long restHour = hours % 24;
        return restHour > 0 ? days + " 天 " + restHour + " 小时" : days + " 天";
    }

    private static JSONObject decorateVisit(JSONObject v, String offset) {
        JSONObject out = new JSONObject();
        try {
            Iterator<String> names = v.keys();
            while (names.hasNext()) {
                String k = names.next();
                out.put(k, v.opt(k));
            }
            String iso = v.optString("at", "");
            long t = 0;
            if (!iso.isEmpty()) t = java.time.Instant.parse(iso).toEpochMilli();
            long now = System.currentTimeMillis();
            long mins = t > 0 ? Math.max(0, (now - t) / 60000L) : -1;
            out.put("elapsed", mins >= 0 ? describeElapsed(mins) : "");
        } catch (Exception ignored) {
        }
        return out;
    }

    public static JSONObject recordVisit(Context ctx, JSONObject args) {
        try {
            JSONObject visitState = load(ctx, KEY_VISIT, new JSONObject().put("policy", defaultVisitPolicy()).put("visits", new JSONArray()));
            JSONObject policy = visitState.optJSONObject("policy");
            if (policy == null) policy = defaultVisitPolicy();
            String source = args.optString("source", "app");
            String event = args.optString("event", "visit");
            String note = args.optString("note", "用户来找陪伴对象");
            String mood = args.optString("mood", "");
            String conversationHint = args.optString("conversation_hint", "");
            String offset = args.optString("timezone_offset", policy.optString("timezone_offset", "+08:00"));
            int window = Math.max(0, args.optInt("duplicate_window_minutes", policy.optInt("duplicate_window_minutes", 5)));
            long since = System.currentTimeMillis() - (long) window * 60000L;
            JSONArray visits = visitState.optJSONArray("visits");
            if (visits == null) visits = new JSONArray();
            if (window > 0) {
                for (int i = 0; i < visits.length(); i++) {
                    JSONObject v = visits.optJSONObject(i);
                    if (v == null) continue;
                    try {
                        long t = java.time.Instant.parse(v.optString("at", "")).toEpochMilli();
                        if (t >= since && source.equals(v.optString("source", "app")) && event.equals(v.optString("event", "visit"))) {
                            v.put("updated_at", nowIso());
                            if (note != null && !note.isEmpty()) v.put("note", note);
                            if (mood != null && !mood.isEmpty()) v.put("mood", mood);
                            if (conversationHint != null && !conversationHint.isEmpty()) v.put("conversation_hint", conversationHint);
                            v.put("duplicate_hits", v.optInt("duplicate_hits", 0) + 1);
                            save(ctx, KEY_VISIT, visitState);
                            return new JSONObject().put("ok", true).put("entry", decorateVisit(v, offset)).put("duplicate_skipped", true);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            JSONObject entry = new JSONObject();
            entry.put("id", System.currentTimeMillis() + "-" + Long.toHexString(Double.doubleToLongBits(Math.random())));
            entry.put("at", nowIso());
            entry.put("source", source);
            entry.put("event", event);
            entry.put("note", note);
            entry.put("mood", mood);
            entry.put("conversation_hint", conversationHint);
            entry.put("timezone_offset", offset);
            JSONArray next = new JSONArray();
            next.put(entry);
            for (int i = 0; i < visits.length() && next.length() < Math.max(50, policy.optInt("history_limit", 1000)); i++) next.put(visits.opt(i));
            visitState.put("visits", next);
            save(ctx, KEY_VISIT, visitState);
            return new JSONObject().put("ok", true).put("entry", decorateVisit(entry, offset)).put("duplicate_skipped", false);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    private static JSONArray filteredVisits(Context ctx, String source, long sinceMs, String date, String offset) {
        JSONArray out = new JSONArray();
        JSONObject state = load(ctx, KEY_VISIT, visitState());
        JSONArray visits = state.optJSONArray("visits");
        if (visits == null) return out;
        for (int i = 0; i < visits.length(); i++) {
            JSONObject v = visits.optJSONObject(i);
            if (v == null) continue;
            if (source != null && !source.isEmpty() && !source.equals(v.optString("source", ""))) continue;
            String iso = v.optString("at", "");
            try {
                long t = java.time.Instant.parse(iso).toEpochMilli();
                if (sinceMs > 0 && t < sinceMs) continue;
                if (date != null && !date.isEmpty() && !date.equals(localDateKey(iso, offset))) continue;
            } catch (Exception e) {
                continue;
            }
            out.put(decorateVisit(v, offset));
        }
        return out;
    }

    public static JSONObject getVisitStats(Context ctx, JSONObject args) {
        try {
            String source = args.optString("source", "");
            String offset = args.optString("timezone_offset", "");
            JSONObject state = load(ctx, KEY_VISIT, new JSONObject().put("policy", defaultVisitPolicy()).put("visits", new JSONArray()));
            JSONObject policy = state.optJSONObject("policy");
            if (policy == null) policy = defaultVisitPolicy();
            if (offset.isEmpty()) offset = policy.optString("timezone_offset", "+08:00");
            long now = System.currentTimeMillis();
            int sinceHours = Math.max(1, args.optInt("since_hours", 24));
            long sinceMs = now - (long) sinceHours * 3600000L;
            JSONArray visits = filteredVisits(ctx, source, sinceMs, "", offset);
            JSONArray last24 = filteredVisits(ctx, source, now - 86400000L, "", offset);
            String todayKey = localDateKey(nowIso(), offset);
            JSONArray today = filteredVisits(ctx, source, 0, todayKey, offset);
            JSONArray last7 = filteredVisits(ctx, source, now - 7L * 86400000L, "", offset);
            JSONArray all = filteredVisits(ctx, source, 0, "", offset);
            JSONObject last = all.length() > 0 ? all.optJSONObject(0) : null;
            Long minutesSinceLast = null;
            if (last != null) {
                try {
                    long t = java.time.Instant.parse(last.optString("at", "")).toEpochMilli();
                    minutesSinceLast = Math.max(0, (now - t) / 60000L);
                } catch (Exception ignored) {
                }
            }
            int awayThresholdMin = Math.max(1, args.optInt("away_threshold_hours", 12)) * 60;
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("count_in_window", visits.length());
            result.put("window_hours", sinceHours);
            result.put("today_count", today.length());
            result.put("recent_24h_count", last24.length());
            result.put("recent_7d_count", last7.length());
            result.put("last_visit", last == null ? JSONObject.NULL : last);
            result.put("minutes_since_last_visit", minutesSinceLast == null ? JSONObject.NULL : minutesSinceLast);
            result.put("away_signal", minutesSinceLast != null && minutesSinceLast >= awayThresholdMin);
            result.put("meaning", (minutesSinceLast != null && minutesSinceLast >= awayThresholdMin)
                    ? "用户已经有一段时间没回来找陪伴对象，可以在合适时表达想念或结合归电判断。"
                    : "到访节奏正常，适合自然接住，不需要制造压力。");
            return result;
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    public static JSONObject carePolicy(Context ctx) {
        try {
            JSONObject state = load(ctx, KEY_CARE, new JSONObject().put("policy", defaultCarePolicy()).put("history", new JSONArray()));
            JSONObject policy = state.optJSONObject("policy");
            if (policy == null) policy = defaultCarePolicy();
            return new JSONObject().put("ok", true).put("policy", policy);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    public static JSONObject setCarePolicy(Context ctx, JSONObject args) {
        try {
            JSONObject state = load(ctx, KEY_CARE, new JSONObject().put("policy", defaultCarePolicy()).put("history", new JSONArray()));
            JSONObject policy = state.optJSONObject("policy");
            if (policy == null) policy = defaultCarePolicy();
            Iterator<String> keys = args.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if ("quiet_start".equals(k)) {
                    JSONObject qh = policy.optJSONObject("quiet_hours");
                    if (qh == null) qh = new JSONObject();
                    qh.put("start", args.opt(k));
                    policy.put("quiet_hours", qh);
                } else if ("quiet_end".equals(k)) {
                    JSONObject qh = policy.optJSONObject("quiet_hours");
                    if (qh == null) qh = new JSONObject();
                    qh.put("end", args.opt(k));
                    policy.put("quiet_hours", qh);
                } else {
                    policy.put(k, args.opt(k));
                }
            }
            state.put("policy", policy);
            save(ctx, KEY_CARE, state);
            return new JSONObject().put("ok", true).put("policy", policy);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    public static JSONObject recordCareEvent(Context ctx, JSONObject args) {
        try {
            JSONObject state = load(ctx, KEY_CARE, new JSONObject().put("policy", defaultCarePolicy()).put("history", new JSONArray()));
            JSONObject policy = state.optJSONObject("policy");
            if (policy == null) policy = defaultCarePolicy();
            JSONArray history = state.optJSONArray("history");
            if (history == null) history = new JSONArray();
            JSONObject entry = new JSONObject();
            entry.put("id", System.currentTimeMillis() + "-" + Long.toHexString(Double.doubleToLongBits(Math.random())));
            entry.put("at", nowIso());
            Iterator<String> keys = args.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                entry.put(k, args.opt(k));
            }
            JSONArray next = new JSONArray();
            next.put(entry);
            int limit = Math.max(20, policy.optInt("history_limit", 80));
            for (int i = 0; i < history.length() && next.length() < limit; i++) next.put(history.opt(i));
            state.put("history", next);
            save(ctx, KEY_CARE, state);
            return new JSONObject().put("ok", true).put("entry", entry);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    public static JSONObject careHistory(Context ctx, int limit) {
        try {
            JSONObject state = load(ctx, KEY_CARE, new JSONObject().put("history", new JSONArray()));
            JSONArray history = state.optJSONArray("history");
            if (history == null) history = new JSONArray();
            int max = limit <= 0 ? history.length() : Math.min(limit, history.length());
            JSONArray out = new JSONArray();
            for (int i = 0; i < max; i++) out.put(history.opt(i));
            return new JSONObject().put("ok", true).put("history", out);
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }
}
