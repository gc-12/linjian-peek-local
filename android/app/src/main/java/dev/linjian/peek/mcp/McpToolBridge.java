package dev.linjian.peek.mcp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import dev.linjian.peek.ActivityEventStore;
import dev.linjian.peek.AppPrefs;
import dev.linjian.peek.AppGate;
import dev.linjian.peek.CalendarState;
import dev.linjian.peek.CompanionService;
import dev.linjian.peek.CompanionWindowState;
import dev.linjian.peek.DiaryState;
import dev.linjian.peek.FocusMode;
import dev.linjian.peek.GuidianState;
import dev.linjian.peek.LifeState;
import dev.linjian.peek.ScreenshotService;
import dev.linjian.peek.TakeoutState;
import dev.linjian.peek.WeatherLive;
import dev.linjian.peek.WalletState;

/**
 * Maps MCP tool names to the app's existing local capability layer
 * (CompanionService / ScreenshotService / state handlers / local care&visit store).
 * Runs inside the foreground McpLocalService, so activity launches (alarm,
 * open app, focus lock) are allowed from this process.
 */
public final class McpToolBridge {
    private final Context ctx;

    public McpToolBridge(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public JSONObject callTool(String name, JSONObject args) {
        try {
            if (args == null) args = new JSONObject();
            JSONObject out = dispatch(name, args);
            if (out == null) return textError("未知工具：" + name);
            return out;
        } catch (Exception e) {
            return textError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JSONObject dispatch(String name, JSONObject args) throws Exception {
        ScreenshotService svc = ScreenshotService.getInstance();

        // ---- 观察 / 状态 ----
        if ("get_phone_state".equals(name)) {
            JSONObject life = LifeState.collect(ctx);
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("current_package", ScreenshotService.currentPackage());
            o.put("accessibility_ready", svc != null);
            o.put("life_state", life);
            return textResult(o.toString());
        }
        if ("get_life_state".equals(name)) return textResult(LifeState.collect(ctx).toString());
        if ("get_senses_state".equals(name)) {
            JSONObject o = new JSONObject();
            o.put("life_state", LifeState.collect(ctx));
            o.put("guidian_state", GuidianState.handleCommand(ctx, cmd("get_guidian_state")));
            return textResult(o.toString());
        }
        if ("get_screen_nodes".equals(name)) {
            if (svc == null) return textError("无障碍服务未就绪，请先在系统设置中开启掌心窗无障碍");
            if (args.optBoolean("refresh", true)) svc.refreshScreenModel();
            return textResult(svc.screenNodesJson());
        }
        if ("tap_text".equals(name)) {
            if (svc == null) return textError("无障碍服务未就绪");
            return textResult(svc.tapText(args.optString("target_text", args.optString("query", "")), args.optString("match", "contains"), args.optInt("index", 1)).toString());
        }
        if ("input_text".equals(name)) {
            if (svc == null) return textError("无障碍服务未就绪");
            return textResult(svc.inputText(args.optString("text", args.optString("input_text", "")), args.optBoolean("append", false)).toString());
        }
        if ("home".equals(name)) return textResult(svc != null && svc.doHome() ? "home" : "home_failed_or_accessibility_missing");
        if ("back".equals(name)) return textResult(svc != null && svc.doBack() ? "back" : "back_failed_or_accessibility_missing");
        if ("recents".equals(name)) return textResult(svc != null && svc.doRecents() ? "recents" : "recents_failed_or_accessibility_missing");
        if ("screen_off".equals(name) || "turn_screen_off".equals(name) || "lock_screen".equals(name) || "phone_screen_off".equals(name)) {
            return textResult(svc != null && svc.doLockScreen() ? "screen_off" : "screen_off_failed_or_accessibility_missing_or_android_too_old");
        }
        if ("tap".equals(name)) return textResult(svc != null && svc.doTap((float) args.optDouble("x", 0), (float) args.optDouble("y", 0)) ? "tap" : "tap_failed_or_accessibility_missing");
        if ("swipe".equals(name)) {
            boolean ok = svc != null && svc.doSwipe((float) args.optDouble("x1", 0), (float) args.optDouble("y1", 0),
                    (float) args.optDouble("x2", 0), (float) args.optDouble("y2", 0), args.optLong("duration", 350));
            return textResult(ok ? "swipe" : "swipe_failed_or_accessibility_missing");
        }
        if ("open_app".equals(name)) {
            String app = args.optString("app", "");
            String pkg = args.optString("package", args.optString("pkg", ""));
            if (pkg.isEmpty() && app.isEmpty()) return textError("缺少 App 名称或包名");
            if (pkg.isEmpty()) pkg = AppPrefs.packageForApp(ctx, app);
            return textResult(CompanionService.openPackageResult(ctx, pkg));
        }
        if ("set_alarm".equals(name)) return textResult(perform("set_alarm", args).toString());
        if ("send_notification".equals(name)) return textResult(perform("send_notification", args).toString());
        if ("take_screenshot".equals(name) || "peek".equals(name)) {
            if (svc == null) return textError("无障碍服务未就绪，请先在系统设置中开启掌心窗无障碍");
            String base64 = svc.captureScreenshotBase64();
            if (base64 == null || base64.isEmpty()) return textError("截图失败：可能需要重新开启无障碍服务");
            return imageResult(base64);
        }
        if ("latest_screen".equals(name)) {
            return textResult(new JSONObject().put("ok", false).put("note", "本地模式没有远程最近截图；请使用 take_screenshot 获取当前屏幕。").toString());
        }
        if ("wait".equals(name)) return textResult("wait");
        if ("run_sequence".equals(name)) return runSequence(args);
        if ("save_known_app".equals(name)) {
            AppPrefs.saveCustomApp(ctx, args.optString("alias", args.optString("app", "")), args.optString("package", args.optString("pkg", "")));
            return textResult("saved_known_app:" + args.optString("alias", "") + "=" + args.optString("package", ""));
        }
        if ("linjian_status".equals(name)) {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("mode", "local");
            o.put("local_mcp", true);
            o.put("server_url", AppPrefs.server(ctx));
            o.put("token_set", !AppPrefs.token(ctx).isEmpty());
            o.put("accessibility_ready", svc != null);
            return textResult(o.toString());
        }
        if ("get_weather_state".equals(name)) {
            String city = args.optString("city", AppPrefs.get(ctx).getString(AppPrefs.KEY_CITY, ""));
            JSONObject w = WeatherLive.cached(ctx, city);
            JSONObject o = new JSONObject();
            o.put("ok", w != null);
            o.put("city", city);
            o.put("weather", w);
            if (w != null) o.put("advice", WeatherLive.advice(w, city));
            return textResult(o.toString());
        }
        if ("send_weather_notification".equals(name)) {
            String city = args.optString("city", AppPrefs.get(ctx).getString(AppPrefs.KEY_CITY, ""));
            JSONObject w = WeatherLive.cached(ctx, city);
            String msg = w != null ? WeatherLive.advice(w, city) : "宝宝，" + city + "天气没查到，先按体感穿衣。";
            boolean ok = CompanionService.showReminderNotification(ctx, "天气提醒", msg);
            return textResult(new JSONObject().put("ok", ok).put("message", msg).toString());
        }

        // ---- 守护日历 ----
        if ("get_guardian_calendar".equals(name)) return stateResult(CalendarState.handleCommand(ctx, cmd("get_calendar_state")));
        if ("add_guardian_calendar_event".equals(name) || "add_guardian_day".equals(name)) {
            JSONObject c = copyCmd("add_calendar_event", args);
            return stateResult(CalendarState.handleCommand(ctx, c));
        }
        if ("update_guardian_day".equals(name)) {
            JSONObject c = copyCmd("upsert_calendar_event", args);
            return stateResult(CalendarState.handleCommand(ctx, c));
        }
        if ("list_guardian_days".equals(name)) {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("events", CalendarState.events(ctx));
            return textResult(o.toString());
        }
        if ("delete_guardian_day".equals(name)) {
            String id = args.optString("id", "");
            boolean ok = CalendarState.deleteEvent(ctx, id);
            return textResult(new JSONObject().put("ok", ok).put("result", ok ? "deleted:" + id : "not_found:" + id).toString());
        }

        // ---- TA 的日记 ----
        if (DIARY.contains(name)) return stateResult(DiaryState.handleCommand(ctx, copyCmd(name, args)));

        // ---- 归电 ----
        if (GUIDIAN.contains(name)) return stateResult(GuidianState.handleCommand(ctx, copyCmd(name, args)));

        // ---- 专注模式 ----
        if (FOCUS.contains(name)) {
            JSONObject rr = FocusMode.handleCommand(ctx, copyCmd(name, args));
            if (("start_focus_mode".equals(name) || "enable_focus_mode".equals(name)) && rr.optBoolean("ok", false)) {
                FocusMode.forceShowLockActivity(ctx);
            }
            return stateResult(rr);
        }

        // ---- 应用门禁 / 屏幕休息 ----
        if (GATE.contains(name)) {
            String action = normalizeGateAction(name);
            return stateResult(AppGate.handleCommand(ctx, copyCmd(action, args)));
        }

        // ---- 小金库 ----
        if (WALLET.contains(name)) return stateResult(WalletState.handleCommand(ctx, copyCmd(name, args)));

        // ---- 外卖小助手 ----
        if (TAKEOUT.contains(name)) return stateResult(TakeoutState.handleCommand(ctx, copyCmd(name, args)));

        // ---- 小金库/外卖统一入口 ----
        if ("wallet_takeout_action".equals(name)) {
            String action = args.optString("action", "");
            JSONObject merged = new JSONObject();
            try {
                String payloadJson = args.optString("payload_json", "{}");
                JSONObject payload = new JSONObject(payloadJson);
                java.util.Iterator<String> keys = payload.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    merged.put(k, payload.opt(k));
                }
            } catch (Exception ignored) {
            }
            java.util.Iterator<String> keys = args.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if ("payload_json".equals(k)) continue;
                merged.put(k, args.opt(k));
            }
            if (WALLET.contains(action)) return stateResult(WalletState.handleCommand(ctx, copyCmd(action, merged)));
            if (TAKEOUT.contains(action)) return stateResult(TakeoutState.handleCommand(ctx, copyCmd(action, merged)));
            return textError("unsupported_wallet_takeout_action: " + action);
        }

        // ---- 窗语 / 活动事件 ----
        if ("get_window_whisper".equals(name)) {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("whisper", CompanionWindowState.whisper(ctx));
            return textResult(o.toString());
        }
        if ("set_window_whisper".equals(name)) {
            String content = args.optString("content", "");
            String author = args.optString("author", "陪伴对象");
            CompanionWindowState.updateWhisper(ctx, content, author, null);
            return textResult(new JSONObject().put("ok", true).put("content", content).put("author", author).toString());
        }
        if ("get_companion_actions".equals(name)) {
            int limit = args.optInt("limit", 20);
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("actions", CompanionWindowState.actions(ctx));
            return textResult(o.toString());
        }
        if ("get_activity_events".equals(name)) {
            int limit = args.optInt("limit", 50);
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("events", ActivityEventStore.list(ctx, args.optString("source", ""), limit, args.optBoolean("today_only", false)));
            return textResult(o.toString());
        }
        if ("add_activity_event".equals(name)) {
            JSONObject input = new JSONObject();
            java.util.Iterator<String> keys = args.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                input.put(k, args.opt(k));
            }
            return textResult(ActivityEventStore.add(ctx, input, false).toString());
        }

        // ---- 关心策略 / 到访记录 ----
        if ("get_care_policy".equals(name)) return textResult(McpCareVisit.carePolicy(ctx).toString());
        if ("set_care_policy".equals(name)) return textResult(McpCareVisit.setCarePolicy(ctx, args).toString());
        if ("record_care_event".equals(name)) return textResult(McpCareVisit.recordCareEvent(ctx, args).toString());
        if ("get_care_history".equals(name)) return textResult(McpCareVisit.careHistory(ctx, args.optInt("limit", 20)).toString());
        if ("record_visit".equals(name)) return textResult(McpCareVisit.recordVisit(ctx, args).toString());
        if ("get_last_visit".equals(name)) {
            JSONObject stats = McpCareVisit.getVisitStats(ctx, new JSONObject().put("since_hours", 24).put("source", args.optString("source", "")));
            return textResult(stats.toString());
        }
        if ("get_visit_history".equals(name)) {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("visits", visitHistory(args));
            return textResult(o.toString());
        }
        if ("get_visit_stats".equals(name)) return textResult(McpCareVisit.getVisitStats(ctx, args).toString());
        if ("active_care_check".equals(name)) {
            JSONObject life = LifeState.collect(ctx);
            JSONObject guidian = GuidianState.handleCommand(ctx, cmd("get_guidian_state"));
            JSONObject gate = AppGate.handleCommand(ctx, cmd("get_lock_state"));
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("summary", "已读取本地上下文：当前 App=" + ScreenshotService.currentPackage() + "；归电状态=" + (guidian.optBoolean("ok", false) ? "正常" : "未配置"));
            o.put("current_app", ScreenshotService.currentPackage());
            o.put("accessibility_ready", svc != null);
            o.put("life_state", life);
            o.put("guidian_state", guidian);
            o.put("lock_state", gate);
            o.put("recent_care_events", McpCareVisit.careHistory(ctx, 5).optJSONArray("history"));
            return textResult(o.toString());
        }
        if ("care_action".equals(name)) {
            String action = args.optString("action", "send_notification");
            if ("trigger_guidian".equals(action)) {
                return stateResult(GuidianState.handleCommand(ctx, copyCmd("trigger_guidian", args)));
            }
            return textResult(perform(action, args).toString());
        }

        return null;
    }

    private JSONArray visitHistory(JSONObject args) throws Exception {
        JSONArray out = new JSONArray();
        String source = args.optString("source", "");
        int limit = Math.max(1, Math.min(args.optInt("limit", 20), 100));
        long sinceMs = args.optInt("since_hours", 0) > 0 ? System.currentTimeMillis() - (long) args.optInt("since_hours", 0) * 3600000L : 0;
        String offset = args.optString("timezone_offset", "+08:00");
        JSONObject state = new JSONObject();
        try {
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("linjian_mcp_care", Context.MODE_PRIVATE);
            String raw = prefs.getString("mcp_visit_state", "");
            if (raw != null && !raw.isEmpty()) state = new JSONObject(raw);
        } catch (Exception ignored) {
        }
        JSONArray visits = state.optJSONArray("visits");
        if (visits == null) return out;
        for (int i = 0; i < visits.length() && out.length() < limit; i++) {
            JSONObject v = visits.optJSONObject(i);
            if (v == null) continue;
            if (source != null && !source.isEmpty() && !source.equals(v.optString("source", ""))) continue;
            String iso = v.optString("at", "");
            try {
                long t = java.time.Instant.parse(iso).toEpochMilli();
                if (sinceMs > 0 && t < sinceMs) continue;
            } catch (Exception e) {
                continue;
            }
            out.put(v);
        }
        return out;
    }

    private JSONObject runSequence(JSONObject args) throws Exception {
        JSONArray steps = args.optJSONArray("steps");
        if (steps == null) {
            JSONObject payload = args.optJSONObject("payload");
            if (payload != null) steps = payload.optJSONArray("steps");
        }
        JSONArray report = new JSONArray();
        boolean allOk = true;
        int executed = 0;
        boolean stopOnError = args.optBoolean("stop_on_error", true);
        int count = steps == null ? 0 : Math.min(12, steps.length());
        for (int i = 0; i < count; i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step == null) step = new JSONObject();
            JSONObject r;
            try {
                r = perform(step.optString("action", "noop"), step);
                int wait = Math.max(0, Math.min(5000, step.optInt("wait_ms", step.optInt("delay_ms", 0))));
                if (wait > 0) Thread.sleep(wait);
            } catch (Exception e) {
                r = new JSONObject().put("ok", false).put("result", e.getMessage());
            }
            boolean ok = r.optBoolean("ok", false);
            report.put(new JSONObject().put("index", i + 1).put("label", step.optString("label", step.optString("action", "")))
                    .put("action", step.optString("action", "noop")).put("ok", ok).put("detail", r));
            if (ok) executed++;
            else {
                allOk = false;
                if (stopOnError) break;
            }
        }
        JSONObject o = new JSONObject();
        o.put("ok", allOk);
        o.put("executed", executed);
        o.put("total", count);
        o.put("current_package", ScreenshotService.currentPackage());
        o.put("steps", report);
        return textResult(o.toString());
    }

    /** Calls CompanionService.performAction with the app/payload fields pulled from args. */
    private JSONObject perform(String action, JSONObject args) {
        return CompanionService.performAction(ctx, action,
                args.optString("app", ""), args.optString("package", args.optString("pkg", "")),
                (float) args.optDouble("x", 0), (float) args.optDouble("y", 0),
                (float) args.optDouble("x1", 0), (float) args.optDouble("y1", 0),
                (float) args.optDouble("x2", 0), (float) args.optDouble("y2", 0),
                args.optLong("duration", 350),
                args.optInt("hour", -1), args.optInt("minute", -1),
                args.optString("title", "掌心窗提醒"),
                args.optString("message", args.optString("text", "")),
                args.optBoolean("vibrate", true),
                "", "",
                args.optBoolean("skip_ui", true),
                args.optString("target_text", args.optString("query", "")),
                args.optString("text", args.optString("input_text", "")),
                args.optString("match", "contains"),
                args.optInt("index", 1),
                args.optBoolean("append", false));
    }

    private static String normalizeGateAction(String action) {
        if ("screen_break_app".equals(action) || "start_screen_break".equals(action) || "screen_break".equals(action)) return "lock_app";
        if ("end_screen_break".equals(action) || "stop_screen_break".equals(action)) return "unlock_app";
        if ("temporary_screen_break_release".equals(action) || "temporary_screen_release".equals(action)) return "temporary_unlock_app";
        if ("extend_screen_break".equals(action)) return "extend_lock";
        if ("deny_screen_break_release_request".equals(action) || "deny_break_release_request".equals(action)) return "deny_unlock_request";
        if ("get_screen_break_state".equals(action)) return "get_lock_state";
        if ("set_screen_break_passphrase".equals(action)) return "set_emergency_passphrase";
        if ("add_screen_break_app".equals(action)) return "add_locked_app";
        if ("remove_screen_break_app".equals(action)) return "remove_locked_app";
        if ("list_screen_break_apps".equals(action)) return "list_lockable_apps";
        return action;
    }

    private static JSONObject cmd(String action) throws Exception {
        return new JSONObject().put("action", action);
    }

    private static JSONObject copyCmd(String action, JSONObject args) throws Exception {
        JSONObject c = new JSONObject();
        java.util.Iterator<String> keys = args.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            try {
                c.put(k, args.opt(k));
            } catch (Exception ignored) {
            }
        }
        c.put("action", action);
        return c;
    }

    private static JSONObject stateResult(JSONObject rr) {
        return textResult(rr == null ? "{}" : rr.toString());
    }

    private static JSONObject textResult(String text) {
        JSONObject content = new JSONObject();
        try {
            content.put("type", "text");
            content.put("text", text);
        } catch (Exception ignored) {
        }
        JSONObject out = new JSONObject();
        try {
            out.put("content", new JSONArray().put(content));
            out.put("isError", false);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static JSONObject textError(String text) {
        JSONObject out = textResult(text);
        try {
            out.put("isError", true);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static JSONObject imageResult(String base64) {
        JSONObject img = new JSONObject();
        try {
            img.put("type", "image");
            img.put("data", base64);
            img.put("mimeType", "image/jpeg");
        } catch (Exception ignored) {
        }
        JSONObject out = new JSONObject();
        try {
            out.put("content", new JSONArray().put(img));
            out.put("isError", false);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static final Set<String> DIARY = new HashSet<>(Arrays.asList(
            "create_diary_book", "list_diary_books", "rename_diary_book", "update_diary_book_cover",
            "write_diary_entry", "list_diary_entries", "read_diary_entry", "search_diary_entries",
            "update_diary_entry", "delete_diary_entry", "delete_diary_book"));

    private static final Set<String> GUIDIAN = new HashSet<>(Arrays.asList(
            "get_guidian_state", "set_guidian_config", "trigger_guidian", "mark_guidian_returned"));

    private static final Set<String> FOCUS = new HashSet<>(Arrays.asList(
            "get_focus_status", "start_focus_mode", "end_focus_mode", "set_focus_plan",
            "reply_focus_request", "approve_focus_unlock", "deny_focus_unlock"));

    private static final Set<String> GATE = new HashSet<>(Arrays.asList(
            "lock_app", "screen_break_app", "start_screen_break", "screen_break",
            "unlock_app", "end_screen_break", "stop_screen_break",
            "temporary_unlock_app", "temporary_screen_break_release", "temporary_screen_release",
            "extend_lock", "extend_screen_break",
            "deny_unlock_request", "deny_screen_break_release_request",
            "get_lock_state", "get_screen_break_state",
            "set_emergency_passphrase", "set_screen_break_passphrase",
            "add_locked_app", "add_screen_break_app",
            "remove_locked_app", "remove_screen_break_app",
            "list_lockable_apps", "list_screen_break_apps"));

    private static final Set<String> WALLET = new HashSet<>(Arrays.asList(
            "get_wallet_state", "get_wallet_month_state", "list_wallet_months",
            "add_wallet_record", "edit_wallet_record", "delete_wallet_record", "remove_wallet_record", "update_wallet_record",
            "list_wallet_pending", "list_wallet_approvals",
            "submit_wallet_approval", "submit_companion_wallet_request", "submit_wallet_request",
            "list_companion_wallet_requests", "list_wallet_request_results",
            "confirm_wallet_record", "decide_wallet_approval",
            "save_wallet_request_result", "update_wallet_request_result", "save_user_wallet_request_result",
            "get_wallet_rules", "set_wallet_rules", "wallet_approval_request", "open_wallet"));

    private static final Set<String> TAKEOUT = new HashSet<>(Arrays.asList(
            "get_takeout_state", "set_takeout_budget", "set_takeout_preferences",
            "add_takeout_card", "save_takeout_card", "update_takeout_card", "delete_takeout_card", "remove_takeout_card",
            "list_takeout_cards", "list_takeout_meals",
            "remember_takeout_meal", "remember_current_takeout_meal",
            "suggest_takeout_options", "create_takeout_plan",
            "takeout_wallet_request", "open_takeout_link", "open_takeout_plan",
            "copy_takeout_note", "record_takeout_order",
            "prepare_takeout_checkout", "auto_takeout_checkout",
            "get_takeout_checkout_status", "cancel_takeout_checkout"));
}
