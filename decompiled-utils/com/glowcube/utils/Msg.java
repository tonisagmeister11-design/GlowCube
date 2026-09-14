/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

public final class Msg {
    public static final String SERVER_NAME = "3A SMP";
    public static final String GRADIENT = "<gradient:#55FFFF:#5555FF>";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#55FFFF:#5555FF>\u2726</gradient> <gray>";
    private static final TagResolver DEFAULTS = Placeholder.unparsed((String)"name_lit", (String)"<name>");

    private Msg() {
    }

    public static Component mm(String string, TagResolver ... tagResolverArray) {
        return MM.deserialize(string, TagResolver.resolver((TagResolver[])new TagResolver[]{DEFAULTS, TagResolver.resolver((TagResolver[])tagResolverArray)}));
    }

    public static TagResolver ph(String string, String string2) {
        return Placeholder.unparsed((String)string, (String)string2);
    }

    public static void info(CommandSender commandSender, String string, TagResolver ... tagResolverArray) {
        commandSender.sendMessage(Msg.mm(PREFIX + string, tagResolverArray));
    }

    public static void success(CommandSender commandSender, String string, TagResolver ... tagResolverArray) {
        commandSender.sendMessage(Msg.mm("<gradient:#55FFFF:#5555FF>\u2726</gradient> <gray><green>" + string, tagResolverArray));
    }

    public static void error(CommandSender commandSender, String string, TagResolver ... tagResolverArray) {
        commandSender.sendMessage(Msg.mm("<gradient:#55FFFF:#5555FF>\u2726</gradient> <gray><red>" + string, tagResolverArray));
    }
}

