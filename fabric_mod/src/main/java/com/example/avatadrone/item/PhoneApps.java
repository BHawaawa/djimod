package com.example.avatadrone.item;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Desktop apps of the Redmi K90.
 *
 * An app is just an id: its icon is  assets/djimod/textures/item/phone/apps/&lt;id&gt;.png  (96x96, rounded square,
 * transparent corners) and its label is the lang key  app.djimod.phone.&lt;id&gt;  (falls back to the id).
 * To add a new app: drop the PNG (mod jar or resource pack), add the lang line, optionally {@link #register} it so
 * the command suggests it, then install it on a phone with {@link #install} or  /redmi app add &lt;id&gt;.
 *
 * NBT: "Apps" = desktop grid (4 x 5 = 20 slots, in order), "Dock" = bottom dock (4 slots).
 */
public final class PhoneApps {
    private PhoneApps() {}
    public static final int GRID_SLOTS = 20, DOCK_SLOTS = 4;
    public static final List<String> DEFAULT_GRID = List.of("clock", "weather", "calendar", "gallery", "settings", "appstore",
            "music", "files", "notes", "calculator", "themes", "security", "mihome", "djifly", "nas");
    public static final List<String> DEFAULT_DOCK = List.of("phone", "messages", "browser", "camera");
    private static final Set<String> KNOWN = new LinkedHashSet<>(List.of("phone", "messages", "browser", "camera", "clock",
            "weather", "calendar", "gallery", "settings", "appstore", "music", "files", "notes", "calculator", "themes",
            "security", "mihome", "djifly", "nas", "compass", "recorder"));

    /** make an app id known to the command suggestions (icon / label must exist in the assets). */
    public static void register(String id) { KNOWN.add(id); }
    public static Set<String> known() { return KNOWN; }

    public static List<String> grid(ItemStack s) { return read(s, "Apps", DEFAULT_GRID); }
    public static List<String> dock(ItemStack s) { return read(s, "Dock", DEFAULT_DOCK); }

    private static List<String> read(ItemStack s, String key, List<String> def) {
        NbtCompound n = s.getNbt();
        if (n == null || !n.contains(key, NbtElement.LIST_TYPE)) return def;
        NbtList l = n.getList(key, NbtElement.STRING_TYPE);
        List<String> out = new ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    private static void write(ItemStack s, String key, List<String> ids) {
        NbtList l = new NbtList();
        for (String id : ids) l.add(NbtString.of(id));
        s.getOrCreateNbt().put(key, l);
    }

    /** @return false if the target area is full or the app is already installed there */
    public static boolean install(ItemStack s, String id, boolean toDock) {
        List<String> list = new ArrayList<>(toDock ? dock(s) : grid(s));
        if (list.contains(id) || list.size() >= (toDock ? DOCK_SLOTS : GRID_SLOTS)) return false;
        list.add(id);
        write(s, toDock ? "Dock" : "Apps", list);
        return true;
    }

    public static boolean uninstall(ItemStack s, String id) {
        List<String> g = new ArrayList<>(grid(s)), d = new ArrayList<>(dock(s));
        boolean hit = g.remove(id) | d.remove(id);
        if (hit) { write(s, "Apps", g); write(s, "Dock", d); }
        return hit;
    }

    public static void reset(ItemStack s) {
        if (s.hasNbt()) { s.getNbt().remove("Apps"); s.getNbt().remove("Dock"); }
    }

    // ------------------------------------------------------------------ /redmi app add|remove|list|reset
    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
            CommandManager.literal("redmi").then(CommandManager.literal("app")
                .then(CommandManager.literal("list").executes(c -> {
                    ItemStack s = phone(c.getSource().getPlayerOrThrow());
                    c.getSource().sendFeedback(() -> Text.literal("桌面: " + grid(s) + "  Dock: " + dock(s)), false);
                    return 1;
                }))
                .then(CommandManager.literal("reset").executes(c -> {
                    reset(phone(c.getSource().getPlayerOrThrow()));
                    c.getSource().sendFeedback(() -> Text.literal("已恢复默认桌面"), false);
                    return 1;
                }))
                .then(CommandManager.literal("add").then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests((c, b) -> CommandSource.suggestMatching(KNOWN, b))
                    .executes(c -> add(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "id"), false))
                    .then(CommandManager.literal("dock")
                        .executes(c -> add(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "id"), true)))))
                .then(CommandManager.literal("remove").then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests((c, b) -> CommandSource.suggestMatching(KNOWN, b))
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        boolean ok = uninstall(phone(p), StringArgumentType.getString(c, "id"));
                        p.sendMessage(Text.literal(ok ? "已卸载" : "桌面上没有这个应用"), true);
                        return ok ? 1 : 0;
                    }))))));
    }

    private static int add(ServerPlayerEntity p, String id, boolean dock) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!id.matches("[a-z0-9_]+")) {
            p.sendMessage(Text.literal("应用 ID 只能包含小写字母、数字和下划线"), true);
            return 0;
        }
        boolean ok = install(phone(p), id, dock);
        p.sendMessage(Text.literal(ok ? "已安装 " + id : "无法安装（已存在或位置已满）"), true);
        return ok ? 1 : 0;
    }

    private static ItemStack phone(ServerPlayerEntity p) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack s = p.getMainHandStack();
        if (!(s.getItem() instanceof PhoneItem))
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Text.literal("请主手拿着 Redmi K90")).create();
        return s;
    }
}
