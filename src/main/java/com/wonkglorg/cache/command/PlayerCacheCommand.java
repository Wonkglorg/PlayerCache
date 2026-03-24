package com.wonkglorg.cache.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wonkglorg.cache.PlayerCache;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class PlayerCacheCommand extends AbstractCommand{
	
	@Override
	public LiteralArgumentBuilder<CommandSourceStack> argumentBuilder() {
		//@formatter:off
		return literal("playercache")
				.requires(permissions("playercache.command"))
				.then(literal("reload")
						.requires(permissions("playercache.command.reload"))
						.executes(this::reload))
				.then(literal("load")
						.requires(permissions("playercache.command.load"))
						.then(argument("player",StringArgumentType.word()).executes(this::load)))
				;
		//@formatter:on
	}
	
	private int load(CommandContext<CommandSourceStack> ctx) {
		String argument = getArgument(ctx, "player", String.class, null);
		if(argument == null) return -1;
		
		CommandSender sender = ctx.getSource().getSender();
		try{
			UUID uuid = UUID.fromString(argument);
			PlayerCache.getInstance().dataCache().getProfile(uuid).thenAccept(p -> {
				if(p == null){
					sender.sendMessage(toComponent("<red>No Player with this uuid exists!"));
					return;
				}
				sender.sendMessage(toComponent(
						"<green>Loaded Player: <gold>%s<gray>(%s) <blue>first_joined:%s <yellow>last_seen:%s <gray>is_bedrock: %s".formatted(p.name(),
								p.uuid(),
								p.firstJoined(),
								p.lastSeen(),
								p.isBedrock() ? "<green>true" : "<red>false")));
			});
		} catch(Exception e){
			PlayerCache.getInstance().dataCache().getProfile(argument).thenAccept(p -> {
				if(p == null){
					sender.sendMessage(toComponent("<red>No Player with this name exists!"));
					return;
				}
				sender.sendMessage(toComponent(
						"<green>Loaded Player: <gold>%s<gray>(%s) <blue>first_joined:%s <yellow>last_seen:%s <gray>is_bedrock: %s".formatted(p.name(),
								p.uuid(),
								p.firstJoined(),
								p.lastSeen(),
								p.isBedrock() ? "<green>true" : "<red>false")));
			});
		}
		
		return 0;
	}
	
	private int reload(CommandContext<CommandSourceStack> ctx) {
		PlayerCache.getInstance().dataCache().reload();
		return 0;
	}
}
