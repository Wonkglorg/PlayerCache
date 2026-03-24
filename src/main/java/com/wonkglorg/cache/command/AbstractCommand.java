package com.wonkglorg.cache.command;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import static io.papermc.paper.command.brigadier.Commands.literal;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Abstract command to provide some helper functions
 *
 */
public abstract class AbstractCommand{
	private LiteralCommandNode<CommandSourceStack> command;
	
	public abstract LiteralArgumentBuilder<CommandSourceStack> argumentBuilder();
	
	public Set<String> aliases() {
		return Set.of();
	}
	
	public LiteralCommandNode<CommandSourceStack> getCommand() {
		if(command == null){
			command = argumentBuilder().build();
		}
		return command;
	}
	
	public void register(ReloadableRegistrarEvent<Commands> registrar) {
		LiteralCommandNode<CommandSourceStack> node = getCommand();
		registrar.registrar().register(node);
		for(var alias : aliases()){
			registrar.registrar().register(literal(alias).redirect(node).build());
		}
	}
	
	/**
	 * @param permission the permission the sender needs
	 */
	public static Predicate<CommandSourceStack> permissions(String... permission) {
		return c -> {
			if(permission.length == 0) return true;
			return Arrays.stream(permission).allMatch(p -> c.getSender().hasPermission(p));
		};
	}
	
	public static Message toMessage(Component component) {
		return MessageComponentSerializer.message().serialize(component);
	}
	
	public static Message toMessage(String text) {
		return MessageComponentSerializer.message().serialize(MiniMessage.miniMessage().deserialize(text));
	}
	
	public static Component toComponent(String text) {
		return MiniMessage.miniMessage().deserialize(text);
	}
	
	public static <U> U getArgument(CommandContext<CommandSourceStack> context, String argument, Class<U> clazz, U def) {
		try{
			return context.getArgument(argument, clazz);
		} catch(IllegalArgumentException ignored){
			return def;
		}
	}
}
