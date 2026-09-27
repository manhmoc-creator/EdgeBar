      package com.manhmoc.edgebar;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionPackStore {

    private ActionPackStore() { /* utility, cấm khởi tạo */ }

    public static final String LIST_KEY = "apack_action_ids";
    private static final String PREFIX = "apack_action_";

    public static final String OPT_ACTS        = "acts";
    public static final String OPT_DUAL        = "dual";
    public static final String OPT_VIB         = "vib";
    public static final String OPT_SND         = "snd";
    public static final String OPT_ANIM        = "anim";
    public static final String OPT_JUMP_ON     = "jump_on";
    public static final String OPT_OS          = "os";
    public static final String OPT_LAUNCH_PKG  = "launch_pkg";
    public static final String OPT_SHORTCUT_ID = "shortcut_id";

    private static String shared(String id, String opt) { return PREFIX + id + "_" + opt; }
    private static String full(String id, String opt)   { return PREFIX + id + "_full_" + opt; }
    private static String common(String id, String opt) { return PREFIX + id + "_common_" + opt; }
    private static String keyOf(SharedPreferences p, String id, String opt, boolean isCommonSide) {
        if (p.getBoolean(shared(id, OPT_DUAL), false))
            return isCommonSide ? common(id, opt) : full(id, opt);
        return shared(id, opt);
    }

    public static List<String> getPackIds(SharedPreferences prefs) {
        String csv = prefs.getString(LIST_KEY, "");
        List<String> out = new ArrayList<>();
        if (csv.isEmpty()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static String newPackId(SharedPreferences prefs) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        List<String> ids = getPackIds(prefs);
        ids.add(id);
        prefs.edit().putString(LIST_KEY, TextUtils.join(",", ids)).apply();
        return id;
    }

    public static void removePack(SharedPreferences prefs, String id) {
        if (id == null || id.isEmpty()) return;
        List<String> ids = getPackIds(prefs);
        ids.remove(id);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(LIST_KEY, TextUtils.join(",", ids));
        String base = PREFIX + id;
        Map<String, ?> all = prefs.getAll();
        for (String k : all.keySet()) {
            if (k.startsWith(base)) ed.remove(k);
        }
        ed.apply();
    }

    public static boolean isDual(SharedPreferences prefs, String id) {
        return prefs.getBoolean(shared(id, OPT_DUAL), false);
    }
    public static String resolveActs(SharedPreferences prefs, String id, boolean isCommonSide) {
        if (id == null || id.isEmpty()) return "";
        return prefs.getString(keyOf(prefs, id, OPT_ACTS, isCommonSide), "");
    }

    public static String resolveStr(SharedPreferences prefs, String id, String opt, boolean isCommonSide) {
        if (id == null || id.isEmpty()) return "";
        return prefs.getString(keyOf(prefs, id, opt, isCommonSide), "");
    }

    public static boolean resolveBool(SharedPreferences prefs, String id, String opt,
                                      boolean isCommonSide, boolean def) {
        if (id == null || id.isEmpty()) return def;
        return prefs.getBoolean(keyOf(prefs, id, opt, isCommonSide), def);
    }
    public static String getPackForGesture(SharedPreferences prefs, String gestureKey) {
        return prefs.getString(gestureKey + "_apack", "");
    }

    public static void linkGesture(SharedPreferences prefs, String gestureKey, String packId) {
        prefs.edit().putString(gestureKey + "_apack", packId).apply();
    }

    public static void unlinkGesture(SharedPreferences prefs, String gestureKey) {
        prefs.edit().remove(gestureKey + "_apack").apply();
    }
    public static void migrateAllGestures(SharedPreferences prefs,
                                          String[] bars, String[] corners, String[] gestures) {
        List<String> newIds = getPackIds(prefs);
        int before = newIds.size();

        SharedPreferences.Editor ed = prefs.edit();
        String[] prefixes = {"lock_", "home_"};
        for (String prefix : prefixes) {
            for (String bar : bars) {
                migrateOneComponent(prefs, ed, newIds, prefix + bar + "_", gestures);
            }
            for (String corner : corners) {
                migrateOneComponent(prefs, ed, newIds, prefix + "corner_" + corner + "_", gestures);
            }
        }

        if (newIds.size() != before) {
            ed.putString(LIST_KEY, TextUtils.join(",", newIds));
            ed.apply();
        }
    }

    /** Quét 22 gesture của 1 component (VD: lock_r_*). Trả về void — ghi thẳng vào ed. */
    private static void migrateOneComponent(SharedPreferences prefs, SharedPreferences.Editor ed,
                                            List<String> newIds, String compBase, String[] gestures) {
        for (String g : gestures) {
            String gestureKey = compBase + g;

            // Đã link → bỏ qua. Zero-alloc: chỉ 1 lần đọc String key ngắn.
            if (!prefs.getString(gestureKey + "_apack", "").isEmpty()) continue;

            String acts = prefs.getString(gestureKey, "NONE");
            boolean dual = prefs.getBoolean(gestureKey + "_dual", false);
            String commonActs = dual ? prefs.getString(gestureKey + "_lim", "NONE") : "";

            boolean hasSharedActs = !acts.equals("NONE") && !acts.isEmpty();
            boolean hasCommonActs = !commonActs.equals("NONE") && !commonActs.isEmpty();
            if (!hasSharedActs && !hasCommonActs) continue;

            String id = UUID.randomUUID().toString().substring(0, 8);
            newIds.add(id);

            // ---- Option CHUNG (dùng khi dual=false) ----
            ed.putString (shared(id, OPT_ACTS),        acts);
            ed.putString (shared(id, OPT_LAUNCH_PKG),  prefs.getString(gestureKey + "_launch_pkg", ""));
            ed.putString (shared(id, OPT_SHORTCUT_ID), prefs.getString(gestureKey + "_shortcut_id", ""));
            ed.putBoolean(shared(id, OPT_VIB),         prefs.getBoolean(gestureKey + "_vib", true));
            ed.putBoolean(shared(id, OPT_SND),         prefs.getBoolean(gestureKey + "_snd", false));
            ed.putBoolean(shared(id, OPT_ANIM),        prefs.getBoolean(gestureKey + "_anim", true));
            ed.putBoolean(shared(id, OPT_JUMP_ON),     prefs.getBoolean(gestureKey + "_jump_on", true));
            ed.putBoolean(shared(id, OPT_OS),          prefs.getBoolean(gestureKey + "_os", false));

            if (dual) {
                // ---- Tách 2 nửa y hệt cơ chế cũ ----
                ed.putBoolean(shared(id, OPT_DUAL), true);

                // Nửa FULL (Homacc — có Trợ năng)
                ed.putString (full(id, OPT_ACTS),        acts);
                ed.putString (full(id, OPT_LAUNCH_PKG),  prefs.getString(gestureKey + "_launch_pkg", ""));
                ed.putString (full(id, OPT_SHORTCUT_ID), prefs.getString(gestureKey + "_shortcut_id", ""));
                ed.putBoolean(full(id, OPT_VIB),         prefs.getBoolean(gestureKey + "_vib", true));
                ed.putBoolean(full(id, OPT_SND),         prefs.getBoolean(gestureKey + "_snd", false));
                ed.putBoolean(full(id, OPT_ANIM),        prefs.getBoolean(gestureKey + "_anim", true));
                ed.putBoolean(full(id, OPT_JUMP_ON),     prefs.getBoolean(gestureKey + "_jump_on", true));
                ed.putBoolean(full(id, OPT_OS),          prefs.getBoolean(gestureKey + "_os", false));

                // Nửa COMMON (Homeb — không Trợ năng)
                ed.putString (common(id, OPT_ACTS),        commonActs);
                ed.putString (common(id, OPT_LAUNCH_PKG),  prefs.getString(gestureKey + "_lim_launch_pkg", ""));
                ed.putString (common(id, OPT_SHORTCUT_ID), prefs.getString(gestureKey + "_lim_shortcut_id", ""));
                ed.putBoolean(common(id, OPT_VIB),         prefs.getBoolean(gestureKey + "_lim_vib", true));
                ed.putBoolean(common(id, OPT_SND),         prefs.getBoolean(gestureKey + "_lim_snd", false));
                ed.putBoolean(common(id, OPT_ANIM),        prefs.getBoolean(gestureKey + "_lim_anim", true));
                ed.putBoolean(common(id, OPT_JUMP_ON),     prefs.getBoolean(gestureKey + "_lim_jump_on", true));
                ed.putBoolean(common(id, OPT_OS),          prefs.getBoolean(gestureKey + "_lim_os", false));
            }

            // Link gesture -> pack (chỉ ghi vào editor, apply() chung ở cuối hàm migrate)
            ed.putString(gestureKey + "_apack", id);
        }
    }
}
