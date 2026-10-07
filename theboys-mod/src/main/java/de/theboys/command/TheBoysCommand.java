package de.theboys.command;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /theboys power set <players> <power>
 * /theboys power clear <players>
 * /theboys power get <player>
 */
public final class TheBoysCommand {
	private TheBoysCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("theboys")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("power")
						.then(Commands.literal("set")
								.then(Commands.argument("targets", EntityArgument.players())
										.then(Commands.argument("power", StringArgumentType.word())
												.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
														java.util.Arrays.stream(Power.values()).map(Power::id), b))
												.executes(TheBoysCommand::set))))
						.then(Commands.literal("clear")
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(TheBoysCommand::clear)))
						.then(Commands.literal("get")
								.then(Commands.argument("target", EntityArgument.player())
										.executes(TheBoysCommand::get)))));
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "power");
		Power power = null;
		for (Power p : Power.values()) {
			if (p.id().equalsIgnoreCase(name)) power = p;
		}
		if (power == null) {
			ctx.getSource().sendFailure(Component.translatable("command.theboys.unknown_power", name));
			return 0;
		}
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer p : targets) {
			PowerManager.setPower(p, power);
		}
		Power given = power;
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theboys.set", targets.size(), Component.translatable(given.translationKey())), true);
		return targets.size();
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer p : targets) {
			PowerManager.setPower(p, Power.NONE);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theboys.clear", targets.size()), true);
		return targets.size();
	}

	private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(ctx, "target");
		Power power = PowerAttachments.powerOf(p);
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theboys.get", p.getName(), Component.translatable(power.translationKey())), false);
		return power.ordinal();
	}
}
