package com.bionic.creativebox.util;

import org.bukkit.ChatColor;

/** 旧式（&）颜色代码转换；Paper 上更推荐 MiniMessage，这里保持 Bukkit/Spigot 兼容。 */
public final class Text {

    private Text() {
    }

    public static String color(String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }
}
