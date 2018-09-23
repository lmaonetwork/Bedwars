package pro.delfik.bedwars;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pro.delfik.bedwars.game.BWTeam;
import pro.delfik.bedwars.game.Game;
import pro.delfik.bedwars.purchase.Purchase;
import pro.delfik.lmao.ev.EvChat;
import pro.delfik.lmao.user.Person;
import pro.delfik.lmao.util.U;

public class GeneralListener implements Listener {

	@EventHandler(priority = EventPriority.LOW)
	public void chat(AsyncPlayerChatEvent e) {
		Game g = Game.get(e.getPlayer().getWorld());
		e.setCancelled(true);
		if (g == null) {
			EvChat.chat(e.getPlayer().getName(), e.getMessage(), Bedwars.getLobby().getPlayers(), "§7(Лобби) ");
			return;
		}
		BWTeam team = g.getTeam(e.getPlayer().getName());
		String prefix, message = e.getMessage();
		if (team == null) {
			prefix = "§7(Зрители) ";
		} else if (message.charAt(0) == '!') {
			prefix = "§7(Всем) ";
			message = message.substring(1);
		} else {
			TextComponent text = U.constructComponent("§7(" + team.getColor().getPrefix() + "Свои§7) ", e.getPlayer(), "§7: §f" + message);
			for (Person teammate : team.getPlayers()) teammate.msg(text);
			return;
		}
		TextComponent text = U.constructComponent("§7(" + prefix + ") ", e.getPlayer(), "§7: §f" + message);
		EvChat.chat(e.getPlayer().getName(), e.getMessage(), g.getWorld().getPlayers(), prefix);
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK) switch (e.getClickedBlock().getType()) {
			case WORKBENCH:
			case FURNACE:
			case ANVIL:
			case ENCHANTMENT_TABLE:
			case HOPPER:
			case BREWING_STAND:
				e.setCancelled(true);
			case SPONGE:
				if (e.getPlayer().isSneaking()) break;
				e.getPlayer().openInventory(Purchase.getInventory());
				e.setCancelled(true);
				return;
		}
		if (Game.get(e.getPlayer()) != null) return;
		switch (e.getMaterial()) {
			case EMERALD:

		}
	}

	@EventHandler
	public void onCraft(CraftItemEvent e) {
		e.setCancelled(true);
	}

}
