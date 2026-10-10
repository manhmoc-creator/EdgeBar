package com.manhmoc.edgebar;
import android.content.SharedPreferences;
public final class TwinPairStore {
    private TwinPairStore() {}
    public static final String[] BAR_PAIR_IDS = {
"barPair_b_rl","barPair_t_lr","barPair_lr_u","barPair_lr_d","barPair_bt_c","barPair_lr_c"};
    public static final String[][] BAR_PAIR_MEMBERS = {
        {"l","r"},       // barPair_b_rl   — Bottom Left (trái) / Bottom Right (phải)
        {"t_l","t_r"},   // barPair_t_lr   — Top Left / Top Right
        {"l_u","r_u"},   // barPair_lr_u   — Left Up / Right Up
        {"l_d","r_d"},   // barPair_lr_d   — Left Down / Right Down
        {"t_c","b_c"},   // barPair_bt_c   — Top Center / Bottom Center
        {"l_c","r_c"}    // barPair_lr_c   — Left Center / Right Center
    };
    public static final boolean[] BAR_PAIR_SPREAD_X = {true,true,false,false,false,true};
    public static final String[] CORNER_PAIR_IDS = {"cornerPair_t_lr","cornerPair_b_lr"};
    public static final String[][] CORNER_PAIR_MEMBERS = {{"tl","tr"},{"bl","br"}};
    public static final String F_ALPHA="alpha",F_W="w",F_H="h",F_Y="y",F_VIS="vis",
        F_PRI="pri",F_LOCK="lock",F_JUMP="jump",F_SPACING="sp",F_A_EN="a_en",F_B_EN="b_en";
    private static final String FLAG = "twin_pair_migrated_v1";
    public static String findPairOf(String m, boolean isBar){
        String[][] ms = isBar?BAR_PAIR_MEMBERS:CORNER_PAIR_MEMBERS;
        String[] ids = isBar?BAR_PAIR_IDS:CORNER_PAIR_IDS;
        for(int i=0;i<ms.length;i++) if(ms[i][0].equals(m)||ms[i][1].equals(m)) return ids[i];
        return null;
    }
    public static String[] getMembers(String id, boolean isBar){
        String[] ids = isBar?BAR_PAIR_IDS:CORNER_PAIR_IDS;
        String[][] ms = isBar?BAR_PAIR_MEMBERS:CORNER_PAIR_MEMBERS;
        for(int i=0;i<ids.length;i++) if(ids[i].equals(id)) return ms[i];
        return null;
    }
    public static int readInt(SharedPreferences p,String sp,String id,String f,int def,boolean isBar){
        String pk = sp+id+"_"+f;
        if(p.contains(pk)) return p.getInt(pk,def);
        String[] m = getMembers(id,isBar);
        return m==null?def:p.getInt(mk(sp,m[0],f,isBar),def);
    }
    public static void writeInt(SharedPreferences p,String sp,String id,String f,int v,boolean isBar){
        String[] m = getMembers(id,isBar); if(m==null) return;
        p.edit().putInt(sp+id+"_"+f,v)
            .putInt(mk(sp,m[0],f,isBar),v).putInt(mk(sp,m[1],f,isBar),v).apply();
    }
    public static boolean isSideEnabled(SharedPreferences p,String sp,String id,boolean isA,boolean isBar){
    return p.getBoolean(sp+id+"_"+(isA?F_A_EN:F_B_EN),true);
}
public static void setSideEnabled(SharedPreferences p,String sp,String id,boolean isA,boolean en,boolean isBar){
    p.edit().putBoolean(sp+id+"_"+(isA?F_A_EN:F_B_EN),en).apply();
}
    private static String mk(String sp,String m,String f,boolean isBar){
        return isBar?(sp+m+"_"+f):(sp+"corner_"+m+"_"+f);
    }
    public static void migrateIfNeeded(SharedPreferences p){
        if(p.getBoolean(FLAG,false)) return;
        SharedPreferences.Editor ed = p.edit();
        for(String sp : new String[]{"lock_","home_","homacc_"}){
            for(int i=0;i<BAR_PAIR_IDS.length;i++) migOne(p,ed,sp,BAR_PAIR_IDS[i],BAR_PAIR_MEMBERS[i],true);
            for(int i=0;i<CORNER_PAIR_IDS.length;i++) migOne(p,ed,sp,CORNER_PAIR_IDS[i],CORNER_PAIR_MEMBERS[i],false);
        }
        ed.putBoolean(FLAG,true).apply();
    }
    private static void migOne(SharedPreferences p,SharedPreferences.Editor ed,String sp,
            String id,String[] m,boolean isBar){
        if(p.contains(sp+id+"_"+F_ALPHA)) return;
        String kA = isBar?(sp+m[0]+"_"):(sp+"corner_"+m[0]+"_");
        String kB = isBar?(sp+m[1]+"_"):(sp+"corner_"+m[1]+"_");
        String pp = sp+id+"_";
        cp(p,ed,kA,kB,pp,F_ALPHA,50); cp(p,ed,kA,kB,pp,F_W,300);
        cp(p,ed,kA,kB,pp,F_H,60);     cp(p,ed,kA,kB,pp,F_Y,0);
        cp(p,ed,kA,kB,pp,F_VIS,0);    cp(p,ed,kA,kB,pp,F_PRI,0);
        cp(p,ed,kA,kB,pp,F_LOCK,1);   cp(p,ed,kA,kB,pp,F_JUMP,0);
        ed.putInt(pp+F_SPACING,0);
        boolean on = p.getBoolean(kA+"en",false) || p.getBoolean(kB+"en",false);
        ed.putBoolean(pp+F_A_EN,on).putBoolean(pp+F_B_EN,on);
    }
    private static void cp(SharedPreferences p,SharedPreferences.Editor ed,String kA,String kB,
        String pp,String f,int def){
    int v = p.getInt(kA+f,def);
    ed.putInt(pp+f,v);
    if(!p.contains(kA+f)) ed.putInt(kA+f,v);
    if(!p.contains(kB+f)) ed.putInt(kB+f,v);
}
public static String pairIdForBarLoc(int loc){
    if(loc==1||loc==2)   return "barPair_b_rl";   // r, l
    if(loc==7||loc==8)   return "barPair_t_lr";   // t_r, t_l
    if(loc==3||loc==9)   return "barPair_lr_u";   // r_u, l_u
    if(loc==5||loc==11)  return "barPair_lr_d";   // r_d, l_d
    if(loc==0||loc==6)   return "barPair_bt_c";   // b_c, t_c
    if(loc==4||loc==10)  return "barPair_lr_c";   // r_c, l_c
    return null;
}
public static String pairIdForCornerLoc(int loc){
    return (loc==2||loc==3)?"cornerPair_t_lr":"cornerPair_b_lr";
}
public static final String[] BAR_PAIR_LABELS_VI = {
    "Đáy phải/trái", "Đỉnh trái/phải", "Phải/trái trên",
    "Trái/phải dưới", "Đỉnh/đáy giữa", "Trái/phải giữa"};
public static final String[] BAR_PAIR_LABELS_EN = {
    "Bottom R/L", "Top L/R", "Right/Left Up",
    "Left/Right Down", "Top/Bottom Center", "Left/Right Center"};
public static final String[] CORNER_PAIR_LABELS_VI = {"Đỉnh trái/phải", "Đáy trái/phải"};
public static final String[] CORNER_PAIR_LABELS_EN = {"Top L/R", "Bottom L/R"};
public static int repLocOfBarPair(String pairId, String[] BARS){
    String[] m = getMembers(pairId, true);
    if(m == null) return 0;
    for(int i=0;i<BARS.length;i++) if(BARS[i].equals(m[0])) return i;
    return 0;
}
public static int repLocOfCornerPair(String pairId, String[] CORNERS){
    String[] m = getMembers(pairId, false);
    if(m == null) return 0;
    for(int i=0;i<CORNERS.length;i++) if(CORNERS[i].equals(m[0])) return i;
    return 0;
}
public static int pairIdxOfBarLoc(int loc){
    String id = pairIdForBarLoc(loc);
    for(int i=0;i<BAR_PAIR_IDS.length;i++) if(BAR_PAIR_IDS[i].equals(id)) return i;
    return -1;
}
public static int pairIdxOfCornerLoc(int loc){
    String id = pairIdForCornerLoc(loc);
    for(int i=0;i<CORNER_PAIR_IDS.length;i++) if(CORNER_PAIR_IDS[i].equals(id)) return i;
    return -1;
}
public static boolean isOrSplit(SharedPreferences p, String base, boolean isLeft){
    String k = base + (isLeft?"_L_split":"_R_split");
    if(p.contains(k)) return p.getBoolean(k,false);
    return p.getBoolean(base + "_split", false);
}
public static int readOrValue(SharedPreferences p, String base, boolean isLeft, String server, int def){
    String k = base + (isLeft?"_L_":"_R_") + (server.equals("acc")?"acc":"bl");
    if(p.contains(k)) return p.getInt(k,def);
    if(p.getBoolean(base+"_split",false))
        return p.getInt(base + (server.equals("acc")?"_homacc":"_homeb"), p.getInt(base,def));
    return p.getInt(base,def);
}
public static void writeOrValue(SharedPreferences.Editor ed, String base, boolean isLeft, String server, int v){
    ed.putInt(base + (isLeft?"_L_":"_R_") + (server.equals("acc")?"acc":"bl"), v);
}
public static void writeOrSplit(SharedPreferences.Editor ed, String base, boolean isLeft, boolean split){
    ed.putBoolean(base + (isLeft?"_L_split":"_R_split"), split);
}
    public static final int[] BAR_PAIR_MEMBER_A_SIGN = {
        -1,  // barPair_b_rl: {l, r}
        -1,  // barPair_t_lr: {t_l, t_r}
        -1,  // barPair_lr_u: {l_u, r_u}
        -1,  // barPair_lr_d: {l_d, r_d}
         0,  // barPair_bt_c: {t_c, b_c} — spread theo Y
        -1   // barPair_lr_c: {l_c, r_c}
    };

public static final int[] CORNER_PAIR_MEMBER_A_SIGN = {-1, -1};

public static int signOfBarPair(String pairId){
    for(int i=0;i<BAR_PAIR_IDS.length;i++) if(BAR_PAIR_IDS[i].equals(pairId))
        return BAR_PAIR_MEMBER_A_SIGN[i];
    return 0;
}
public static int signOfCornerPair(String pairId){
    for(int i=0;i<CORNER_PAIR_IDS.length;i++) if(CORNER_PAIR_IDS[i].equals(pairId))
        return CORNER_PAIR_MEMBER_A_SIGN[i];
    return -1;
}
public static final String DEFAULT_PACK_FLAG = "default_packs_v";
public static final String[] DEFAULT_BAR_PACK_IDS = {
    "defBarBTC","defBarTLR","defBarBRL","defBarLRC","defBarLRU","defBarLRD"};

public static final String[] DEFAULT_CORNER_PACK_IDS = {"defCornerTLR","defCornerBLR"};
public static final String[] DEFAULT_DISPLAY_ORDER = {
    "corner_defCornerTLR",
    "bar_defBarTLR",
    "bar_defBarBRL",
    "bar_defBarBTC",
    "bar_defBarLRC",
    "bar_defBarLRU",
    "bar_defBarLRD",
    "corner_defCornerBLR"
};
public static final String[] DEFAULT_BAR_PAIR_IDS = {
    "barPair_bt_c","barPair_t_lr","barPair_b_rl","barPair_lr_c","barPair_lr_u","barPair_lr_d"};

public static void ensureDefaultPacks(SharedPreferences p){
    SharedPreferences.Editor ed = p.edit();
    int[] barLocs = {6, 8, 2, 10, 9, 11};
    String[] barNames = {"Top/Bottom Center","Top L/R","Bottom R/L",
        "Left/Right Center","Left/Right Up","Left/Right Down"};
    for(int i = 0; i < DEFAULT_BAR_PACK_IDS.length; i++)
        seedPack(p, ed, true, DEFAULT_BAR_PACK_IDS[i], DEFAULT_BAR_PAIR_IDS[i], barLocs[i], barNames[i]);
    int[] cornerLocs = {3, 1};
    String[] cornerNames = {"Top L/R Corner", "Bottom L/R Corner"};
    for(int i = 0; i < DEFAULT_CORNER_PACK_IDS.length; i++)
        seedPack(p, ed, false, DEFAULT_CORNER_PACK_IDS[i], CORNER_PAIR_IDS[i], cornerLocs[i], cornerNames[i]);
    appendIds(p, ed, "pack_bar_ids", DEFAULT_BAR_PACK_IDS);
    appendIds(p, ed, "pack_corner_ids", DEFAULT_CORNER_PACK_IDS);
    String joined = android.text.TextUtils.join(",", DEFAULT_DISPLAY_ORDER);
    for(String sp : new String[]{"lock_", "home_"}){
        ed.putString(sp + "applied_packs", joined);
        for(String itemKey : DEFAULT_DISPLAY_ORDER){
            if(p.contains(sp + itemKey + "_en")) continue;
            boolean isBar = itemKey.startsWith("bar_");
            String[] m = getMembers(defaultPairOf(itemKey), isBar);
            if(m == null) continue;
            boolean on = p.getBoolean(sp + (isBar ? m[0] : "corner_" + m[0]) + "_en", false)
                || p.getBoolean(sp + (isBar ? m[1] : "corner_" + m[1]) + "_en", false);
            ed.putBoolean(sp + itemKey + "_en", on);
        }
    }
    ed.apply();
}

private static String defaultPairOf(String itemKey){
    boolean isBar = itemKey.startsWith("bar_");
    String id = itemKey.substring(isBar ? 4 : 7);
    String[] ids = isBar ? DEFAULT_BAR_PACK_IDS : DEFAULT_CORNER_PACK_IDS;
    String[] pairs = isBar ? DEFAULT_BAR_PAIR_IDS : CORNER_PAIR_IDS;
    for(int i = 0; i < ids.length; i++) if(ids[i].equals(id)) return pairs[i];
    return null;
}
private static void seedPack(SharedPreferences p, SharedPreferences.Editor ed, boolean isBar,
        String id, String pairId, int loc, String name){
    String base = isBar ? "pack_bar_" : "pack_corner_";
    String px = base + id + "_";
    ed.putInt(px + "loc", loc);
    if(!p.contains(px + "name")) ed.putString(px + "name", name);
    if(!p.contains(px + "pair_spacing")) ed.putInt(px + "pair_spacing", 0);
    String[] m = getMembers(pairId, isBar);
    if(m == null) return;
    if(isBar && !p.contains(px + "w")){
        String src = p.contains("home_" + m[0] + "_w") ? "home_" : "lock_";
        ed.putInt(px + "alpha", p.getInt(src + m[0] + "_alpha", 50))
          .putInt(px + "w", p.getInt(src + m[0] + "_w", 300))
          .putInt(px + "h", p.getInt(src + m[0] + "_h", 60))
          .putInt(px + "y", p.getInt(src + m[0] + "_y", 0));
    }
    String ak = base + pairId + "_" + F_A_EN;
    if(!p.contains(ak)){
        boolean a = memberOn(p, m[0], isBar), b = memberOn(p, m[1], isBar);
        if(!a && !b){ a = true; b = true; }
        ed.putBoolean(ak, a).putBoolean(base + pairId + "_" + F_B_EN, b);
    }
}
private static boolean memberOn(SharedPreferences p, String m, boolean isBar){
    String k = (isBar ? m : "corner_" + m) + "_en";
    return p.getBoolean("lock_" + k, false) || p.getBoolean("home_" + k, false);
}
private static void appendIds(SharedPreferences p, SharedPreferences.Editor ed, String listKey, String[] newIds){
    java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
    String cur = p.getString(listKey, "");
    if(!cur.isEmpty()) for(String s : cur.split(",")) if(!s.trim().isEmpty()) set.add(s.trim());
    for(String id : newIds) set.add(id);
    ed.putString(listKey, android.text.TextUtils.join(",", set));
}
private static String defBarName(String pairId){
    for(int i=0;i<BAR_PAIR_IDS.length;i++) if(BAR_PAIR_IDS[i].equals(pairId)) return BAR_PAIR_LABELS_EN[i];
    return "Bar Pack";
}
}
