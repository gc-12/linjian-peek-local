package dev.linjian.peek.mcp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Full tool catalog for the local MCP server. Schemas stay pragmatic:
 * important parameters are typed, and additionalProperties:true lets the
 * state handlers (wallet/takeout/diary/calendar) receive their full payloads.
 */
public final class McpToolRegistry implements McpToolProvider {
    private final McpToolBridge bridge;
    private final JSONArray tools;

    public McpToolRegistry(Context ctx) {
        this.bridge = new McpToolBridge(ctx);
        this.tools = buildTools();
    }

    @Override
    public JSONArray listTools() {
        return tools;
    }

    @Override
    public JSONObject callTool(String name, JSONObject arguments) {
        return bridge.callTool(name, arguments);
    }

    private static JSONArray buildTools() {
        JSONArray list = new JSONArray();

        list.put(tool("get_phone_state", "读取当前前台 App、无障碍就绪状态与生活状态。",
                p("device_id", "string", "设备标识")));
        list.put(tool("get_life_state", "读取掌心窗生活状态层：电量、充电、网络、当前 App、今日屏幕时间、解锁次数等。",
                p("device_id", "string", "设备标识")));
        list.put(tool("get_senses_state", "读取通用状态：生活状态与归电状态。",
                p("device_id", "string", "设备标识")));
        list.put(tool("get_screen_nodes", "读取当前屏幕无障碍节点：文字、控件类型、坐标。",
                p("refresh", "boolean", "是否强制刷新", "false")));
        list.put(tool("tap_text", "按当前屏幕文字精准点击。",
                p("target_text", "string", "要点击的文字", ""), p("match", "string", "contains/exact", "contains"), p("index", "integer", "第几个匹配", "1")));
        list.put(tool("input_text", "把文字输入到当前聚焦或第一个可编辑输入框。",
                p("text", "string", "要输入的文字", ""), p("append", "boolean", "是否追加", "false")));
        list.put(tool("take_screenshot", "立刻截取当前屏幕并返回图片。", new JSONObject[0]));
        list.put(tool("peek", "立刻截取当前屏幕并返回图片（兼容别名）。", new JSONObject[0]));
        list.put(tool("latest_screen", "本地模式下返回提示（无远程最近截图）。", new JSONObject[0]));
        list.put(tool("open_app", "打开指定 App。",
                p("app", "string", "App 名称，如“小红书”", ""), p("package", "string", "包名，如 com.xingin.xhs", "")));
        list.put(tool("home", "回到桌面。", new JSONObject[0]));
        list.put(tool("back", "返回上一页。", new JSONObject[0]));
        list.put(tool("recents", "打开最近任务。", new JSONObject[0]));
        list.put(tool("screen_off", "锁屏熄屏。", new JSONObject[0]));
        list.put(tool("tap", "在屏幕坐标点击。",
                p("x", "number", "x 坐标", ""), p("y", "number", "y 坐标", "")));
        list.put(tool("swipe", "滑动手势。",
                p("x1", "number", "起点 x", ""), p("y1", "number", "起点 y", ""),
                p("x2", "number", "终点 x", ""), p("y2", "number", "终点 y", ""),
                p("duration", "number", "时长毫秒", "350")));
        list.put(tool("set_alarm", "设置闹钟。",
                p("hour", "integer", "小时 0-23", ""), p("minute", "integer", "分钟 0-59", ""),
                p("message", "string", "闹钟备注", ""), p("vibrate", "boolean", "是否震动", "true")));
        list.put(tool("send_notification", "给手机发送一条提醒通知。",
                p("title", "string", "标题", "掌心窗提醒"), p("message", "string", "内容", "")));
        list.put(tool("send_weather_notification", "把天气关心送到手机。",
                p("city", "string", "城市", "")));
        list.put(tool("get_weather_state", "读取当前天气状态。",
                p("city", "string", "城市", "")));
        list.put(tool("save_known_app", "保存常用 App 别名与包名。",
                p("alias", "string", "别名", ""), p("package", "string", "包名", "")));
        list.put(tool("run_sequence", "按顺序执行一组手机动作。",
                p("steps", "array", "动作步骤数组", ""), p("stop_on_error", "boolean", "出错是否停止", "true")));
        list.put(tool("wait", "等待。", new JSONObject[0]));
        list.put(tool("linjian_status", "检查本地 MCP 与掌心窗配置状态。", new JSONObject[0]));

        list.put(tool("get_guardian_calendar", "读取守护日历：最近纪念日/节日/倒数日。",
                p("device_id", "string", "设备标识")));
        list.put(tool("add_guardian_calendar_event", "添加/更新守护日历事件。",
                p("title", "string", "事件标题，如七夕", ""), p("date", "string", "阳历 yyyy-MM-dd 或 MM-dd，或农历 MM-dd", ""),
                p("date_type", "string", "solar/lunar", "solar"), p("repeat_type", "string", "yearly/none", "yearly"),
                p("group", "string", "分组", ""), p("note", "string", "备注", ""),
                p("remind_days_before", "integer", "提前几天提醒", "3")));
        list.put(tool("list_guardian_days", "查看守护日历完整事件列表及稳定 id。", new JSONObject[0]));
        list.put(tool("add_guardian_day", "添加一条守护日历事件（别名）。",
                p("title", "string", "事件标题", ""), p("date", "string", "日期", "")));
        list.put(tool("update_guardian_day", "按 id 修改守护日历事件。",
                p("id", "string", "事件 id", ""), p("title", "string", "标题", ""), p("date", "string", "日期", "")));
        list.put(tool("delete_guardian_day", "按 id 删除守护日历事件。",
                p("id", "string", "事件 id", "")));

        list.put(tool("create_diary_book", "创建一本 TA 的日记。",
                p("book_name", "string", "日记本名", "")));
        list.put(tool("list_diary_books", "查看日记本列表。", new JSONObject[0]));
        list.put(tool("rename_diary_book", "重命名日记本。",
                p("book_id", "string", "日记本 id", ""), p("new_name", "string", "新名字", ""), p("old_name", "string", "旧名字", "")));
        list.put(tool("update_diary_book_cover", "更新日记本封面样式。",
                p("book_id", "string", "日记本 id", ""), p("cover_style", "string", "封面样式", "")));
        list.put(tool("write_diary_entry", "以 TA/AI 的视角写一篇日记。",
                p("book_id", "string", "日记本 id（可留空）", ""), p("book_name", "string", "日记本名", ""),
                p("title", "string", "标题", ""), p("content", "string", "正文", ""), p("mood", "string", "心情", "")));
        list.put(tool("list_diary_entries", "列出日记条目。",
                p("book_id", "string", "日记本 id", ""), p("date", "string", "日期 yyyy-MM-dd", "")));
        list.put(tool("read_diary_entry", "读取一篇日记正文。",
                p("entry_id", "string", "日记条目 id", "")));
        list.put(tool("search_diary_entries", "搜索日记。",
                p("book_id", "string", "日记本 id", ""), p("keyword", "string", "关键词", "")));
        list.put(tool("update_diary_entry", "修改一篇日记。",
                p("entry_id", "string", "日记条目 id", "")));
        list.put(tool("delete_diary_entry", "删除一篇日记。",
                p("entry_id", "string", "日记条目 id", ""), p("confirm", "boolean", "必须为 true", "")));
        list.put(tool("delete_diary_book", "删除整本日记。",
                p("book_id", "string", "日记本 id", ""), p("confirm", "boolean", "必须为 true", "")));

        list.put(tool("get_guidian_state", "读取归电状态：上次回来、下次最早归电、今日次数等。",
                p("device_id", "string", "设备标识")));
        list.put(tool("set_guidian_config", "调整归电设置。",
                p("enabled", "boolean", "开关", ""), p("interval_minutes", "integer", "间隔", ""),
                p("daily_limit", "integer", "每日上限", ""), p("quiet_start", "string", "安静时段开始 HH:mm", ""),
                p("quiet_end", "string", "安静时段结束 HH:mm", "")));
        list.put(tool("trigger_guidian", "立刻触发一次归电全屏页。",
                p("reason", "string", "理由", "")));
        list.put(tool("mark_guidian_returned", "手动标记用户已回来。",
                p("source", "string", "来源", "mcp")));

        list.put(tool("get_focus_status", "读取专注模式状态。", new JSONObject[0]));
        list.put(tool("start_focus_mode", "开启全机专注模式。",
                p("duration_minutes", "integer", "时长分钟", ""), p("goal", "string", "目标", ""),
                p("message", "string", "留言", "")));
        list.put(tool("end_focus_mode", "结束专注模式。", new JSONObject[0]));
        list.put(tool("set_focus_plan", "保存专注模式计划。",
                p("goal", "string", "目标", ""), p("duration_minutes", "integer", "时长", ""), p("message", "string", "守护文案", "")));
        list.put(tool("reply_focus_request", "给专注页留言回复一句。",
                p("message", "string", "回复内容", "")));
        list.put(tool("approve_focus_unlock", "批准一次专注临时放行。", new JSONObject[0]));
        list.put(tool("deny_focus_unlock", "拒绝专注放行申请。",
                p("message", "string", "说明", "")));

        list.put(tool("get_screen_break_state", "读取应用门禁/屏幕休息状态。",
                p("device_id", "string", "设备标识")));
        list.put(tool("get_lock_state", "读取应用门禁状态（别名）。",
                p("device_id", "string", "设备标识")));
        list.put(tool("lock_app", "让目标 App 暂停一段时间（屏幕休息）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", ""),
                p("duration_minutes", "integer", "时长分钟", "30"), p("reason", "string", "原因", "")));
        list.put(tool("screen_break_app", "开始屏幕休息（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", ""),
                p("duration_minutes", "integer", "时长分钟", "30")));
        list.put(tool("unlock_app", "解除目标 App 门禁。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("end_screen_break", "结束屏幕休息（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("temporary_unlock_app", "临时放行目标 App。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", ""), p("minutes", "integer", "时长分钟", "")));
        list.put(tool("temporary_screen_break_release", "临时放行（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("extend_lock", "延长目标 App 门禁。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", ""), p("minutes", "integer", "延长分钟", "")));
        list.put(tool("extend_screen_break", "延长屏幕休息（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", ""), p("minutes", "integer", "分钟", "")));
        list.put(tool("deny_unlock_request", "拒绝门禁放行申请。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("deny_screen_break_release_request", "拒绝放行申请（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("add_locked_app", "把 App 加入可锁列表。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("add_screen_break_app", "添加屏幕休息 App（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("remove_locked_app", "从可锁列表移除 App。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("remove_screen_break_app", "移除屏幕休息 App（别名）。",
                p("app", "string", "App 名称", ""), p("package", "string", "包名", "")));
        list.put(tool("list_lockable_apps", "列出可锁 App 列表。", new JSONObject[0]));
        list.put(tool("list_screen_break_apps", "列出屏幕休息 App（别名）。", new JSONObject[0]));
        list.put(tool("set_emergency_passphrase", "设置紧急口令。",
                p("passphrase", "string", "口令", "")));
        list.put(tool("set_screen_break_passphrase", "设置紧急口令（别名）。",
                p("passphrase", "string", "口令", "")));

        addWalletTools(list);
        addTakeoutTools(list);

        list.put(tool("wallet_takeout_action", "小金库/外卖统一入口：填 action 调用同名能力。",
                p("action", "string", "目标工具名", ""), p("payload_json", "string", "额外参数 JSON", "{}")));

        list.put(tool("get_window_whisper", "读取共同窗语。", new JSONObject[0]));
        list.put(tool("set_window_whisper", "更新共同窗语。",
                p("content", "string", "窗语内容", ""), p("author", "string", "修改者", "陪伴对象")));
        list.put(tool("get_companion_actions", "读取陪伴对象最近的真实行动记录。",
                p("limit", "integer", "条数", "20")));
        list.put(tool("get_activity_events", "读取统一活动事件。",
                p("source", "string", "来源", ""), p("limit", "integer", "条数", "50"), p("today_only", "boolean", "仅今天", "false")));
        list.put(tool("add_activity_event", "手动写入一条活动事件。",
                p("type", "string", "事件类型", ""), p("title", "string", "标题", ""), p("detail", "string", "详情", "")));

        list.put(tool("get_care_policy", "读取主动关心策略。", new JSONObject[0]));
        list.put(tool("set_care_policy", "设置主动关心策略。",
                p("active_care_enabled", "boolean", "是否启用", ""), p("care_style", "string", "关心风格", ""),
                p("quiet_start", "string", "安静开始 HH:mm", ""), p("quiet_end", "string", "安静结束 HH:mm", "")));
        list.put(tool("record_care_event", "记录一次主动关心动作。",
                p("action", "string", "动作", ""), p("target_app", "string", "目标 App", "")));
        list.put(tool("get_care_history", "读取最近主动关心记录。",
                p("limit", "integer", "条数", "20")));
        list.put(tool("record_visit", "记录一次用户来访。",
                p("source", "string", "来源", "app"), p("note", "string", "备注", ""),
                p("mood", "string", "心情", ""), p("conversation_hint", "string", "对话提示", "")));
        list.put(tool("get_last_visit", "读取最近一次来访。",
                p("source", "string", "来源", "")));
        list.put(tool("get_visit_history", "读取来访记录。",
                p("source", "string", "来源", ""), p("limit", "integer", "条数", "20"), p("since_hours", "integer", "近几小时", "")));
        list.put(tool("get_visit_stats", "统计来访节奏。",
                p("source", "string", "来源", ""), p("since_hours", "integer", "窗口小时", "24")));
        list.put(tool("active_care_check", "主动关心上下文检查。", new JSONObject[0]));
        list.put(tool("care_action", "执行陪伴对象判断好的关心动作。",
                p("action", "string", "动作", "send_notification"), p("app", "string", "目标 App", ""),
                p("title", "string", "标题", ""), p("message", "string", "内容", "")));

        return list;
    }

    private static void addWalletTools(JSONArray list) {
        list.put(tool("get_wallet_state", "读取小金库当前月份：预算、已花、剩余、待处理。",
                p("device_id", "string", "设备标识"), p("wait_seconds", "integer", "等待秒数", "8")));
        list.put(tool("get_wallet_month_state", "读取小金库指定月份详情。",
                p("month", "string", "月份 yyyy-MM", ""), p("device_id", "string", "设备标识")));
        list.put(tool("list_wallet_months", "读取小金库历史月份摘要。",
                p("device_id", "string", "设备标识")));
        list.put(tool("list_wallet_pending", "读取小金库审批列表和待确认账单。",
                p("device_id", "string", "设备标识")));
        list.put(tool("list_wallet_approvals", "读取花钱审批申请和结果。",
                p("month", "string", "月份", ""), p("device_id", "string", "设备标识")));
        list.put(tool("add_wallet_record", "给小金库添加一笔账单。",
                p("amount", "number", "金额", ""), p("type", "string", "expense/income", "expense"),
                p("category", "string", "分类", "其他"), p("merchant", "string", "商家", ""),
                p("note", "string", "备注", ""), p("require_confirm", "boolean", "待确认", "false")));
        list.put(tool("edit_wallet_record", "编辑一条账单。",
                p("id", "string", "账单 id", ""), p("amount", "number", "金额", "")));
        list.put(tool("delete_wallet_record", "删除一条账单。",
                p("id", "string", "账单 id", "")));
        list.put(tool("submit_wallet_approval", "提交一条小金库申请。",
                p("amount", "number", "金额", ""), p("item", "string", "事由", ""),
                p("requester_role", "string", "user/companion", "companion"), p("reason", "string", "理由", "")));
        list.put(tool("submit_companion_wallet_request", "陪伴者向用户提交小金库申请。",
                p("amount", "number", "金额", ""), p("item", "string", "事由", "")));
        list.put(tool("list_companion_wallet_requests", "读取陪伴者提交给用户的申请及结果。",
                p("month", "string", "月份", ""), p("status", "string", "all/waiting/handled", "all")));
        list.put(tool("list_wallet_request_results", "读取申请列表和处理结果。",
                p("requester_role", "string", "all/user/companion", "all"), p("status", "string", "all/waiting/handled", "all")));
        list.put(tool("confirm_wallet_record", "确认/忽略一条待确认账单。",
                p("id", "string", "账单 id", ""), p("decision", "string", "confirm/ignore", "confirm")));
        list.put(tool("decide_wallet_approval", "保存申请处理结果。",
                p("id", "string", "申请 id", ""), p("decision", "string", "approved/held/denied", "approved"), p("message", "string", "备注", "")));
        list.put(tool("save_wallet_request_result", "保存申请处理结果（status: ok/hold/no）。",
                p("id", "string", "申请 id", ""), p("status", "string", "ok/hold/no", "ok"), p("note", "string", "备注", "")));
        list.put(tool("update_wallet_request_result", "更新申请处理结果（别名）。",
                p("id", "string", "申请 id", ""), p("status", "string", "ok/hold/no", "ok")));
        list.put(tool("save_user_wallet_request_result", "保存用户对陪伴者申请的处理结果。",
                p("id", "string", "申请 id", ""), p("status", "string", "ok/hold/no", "ok"), p("note", "string", "理由", "")));
        list.put(tool("get_wallet_rules", "读取小金库预算规则。",
                p("device_id", "string", "设备标识")));
        list.put(tool("set_wallet_rules", "设置小金库预算规则。",
                p("monthly_budget", "number", "月预算", ""), p("approval_threshold", "number", "审批线", "")));
        list.put(tool("wallet_approval_request", "用户想买东西时的即时花钱审批。",
                p("amount", "number", "金额", ""), p("item", "string", "商品", ""), p("reason", "string", "理由", "")));
    }

    private static void addTakeoutTools(JSONArray list) {
        list.put(tool("get_takeout_state", "读取外卖小助手状态。",
                p("device_id", "string", "设备标识")));
        list.put(tool("set_takeout_budget", "设置单餐/今日外卖预算与口味偏好。",
                p("meal_budget", "number", "单餐预算", ""), p("day_budget", "number", "今日预算", ""), p("taste_note", "string", "口味偏好", "")));
        list.put(tool("set_takeout_preferences", "设置外卖口味偏好。",
                p("taste_note", "string", "口味偏好", "")));
        list.put(tool("add_takeout_card", "保存或更新一张常点套餐。",
                p("title", "string", "名称", ""), p("link", "string", "分享链接", ""), p("note", "string", "备注", "")));
        list.put(tool("save_takeout_card", "保存或覆盖一张常点外卖卡片（别名）。",
                p("title", "string", "名称", ""), p("link", "string", "链接", "")));
        list.put(tool("update_takeout_card", "编辑一张常点外卖卡片。",
                p("id", "string", "卡片 id", ""), p("title", "string", "名称", "")));
        list.put(tool("delete_takeout_card", "删除一张常点外卖卡片。",
                p("id", "string", "卡片 id", "")));
        list.put(tool("remove_takeout_card", "删除一张常点外卖卡片（别名）。",
                p("id", "string", "卡片 id", "")));
        list.put(tool("list_takeout_cards", "读取常点外卖库。",
                p("device_id", "string", "设备标识")));
        list.put(tool("list_takeout_meals", "读取已记住的常点外卖（别名）。",
                p("device_id", "string", "设备标识")));
        list.put(tool("remember_takeout_meal", "记住一道具体外卖。",
                p("id", "string", "卡片 id", ""), p("title", "string", "名称", ""), p("direct_link", "string", "直达链接", "")));
        list.put(tool("remember_current_takeout_meal", "把当前剪贴板链接记成常点外卖。",
                p("title", "string", "名称", ""), p("direct_link", "string", "链接", "")));
        list.put(tool("suggest_takeout_options", "按预算和口味推荐 1-3 个外卖建议。",
                p("query", "string", "关键词", ""), p("budget", "number", "预算", ""), p("limit", "integer", "数量", "3")));
        list.put(tool("create_takeout_plan", "基于常点卡片生成点餐行动卡。",
                p("card_id", "string", "卡片 id", ""), p("query", "string", "关键词", ""), p("submit_wallet_request", "boolean", "是否提交小金库申请", "false")));
        list.put(tool("takeout_wallet_request", "把外卖计划提交到小金库申请。",
                p("card_id", "string", "卡片 id", ""), p("amount", "number", "金额", "")));
        list.put(tool("open_takeout_link", "打开已保存的外卖链接。",
                p("card_id", "string", "卡片 id", ""), p("link", "string", "链接", "")));
        list.put(tool("open_takeout_plan", "打开最近或指定的外卖计划链接。",
                p("card_id", "string", "卡片 id", "")));
        list.put(tool("copy_takeout_note", "复制外卖下单备注。",
                p("card_id", "string", "卡片 id", ""), p("note", "string", "备注", "")));
        list.put(tool("record_takeout_order", "把外卖记入小金库饮食分类。",
                p("amount", "number", "金额", ""), p("merchant", "string", "商家", "")));
        list.put(tool("prepare_takeout_checkout", "整单自动点外卖到付款页（绝不点支付）。",
                p("card_id", "string", "卡片 id", ""), p("query", "string", "关键词", ""),
                p("max_total", "number", "预算上限", ""), p("submit_order", "boolean", "提交订单", "true")));
        list.put(tool("auto_takeout_checkout", "prepare_takeout_checkout 别名。",
                p("card_id", "string", "卡片 id", ""), p("query", "string", "关键词", "")));
        list.put(tool("get_takeout_checkout_status", "读取自动点单状态。",
                p("device_id", "string", "设备标识")));
        list.put(tool("cancel_takeout_checkout", "停止正在进行的自动点单任务。",
                p("reason", "string", "原因", "user_cancelled")));
    }

    private static JSONObject tool(String name, String desc, JSONObject... properties) {
        JSONObject schema = new JSONObject();
        try {
            JSONObject props = new JSONObject();
            for (JSONObject p : properties) {
                if (p == null) continue;
                java.util.Iterator<String> keys = p.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    props.put(k, p.opt(k));
                }
            }
            schema.put("type", "object");
            schema.put("properties", props);
            schema.put("additionalProperties", true);
        } catch (Exception ignored) {
        }
        JSONObject out = new JSONObject();
        try {
            out.put("name", name).put("description", desc).put("inputSchema", schema);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static JSONObject p(String key, String type, String desc) {
        return p(key, type, desc, null);
    }

    private static JSONObject p(String key, String type, String desc, String defaultValue) {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            if (desc != null && !desc.isEmpty()) o.put("description", desc);
            if (defaultValue != null && !defaultValue.isEmpty()) {
                if ("integer".equals(type)) o.put("default", Integer.parseInt(defaultValue));
                else if ("number".equals(type)) o.put("default", Double.parseDouble(defaultValue));
                else if ("boolean".equals(type)) o.put("default", Boolean.parseBoolean(defaultValue));
                else o.put("default", defaultValue);
            }
        } catch (Exception ignored) {
        }
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put(key, o);
        } catch (Exception ignored) {
        }
        return wrapper;
    }
}
