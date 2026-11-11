package com.runebeacon.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Item names and stackability for the panel. Item compositions can only be read on the client
 * thread, so lookups hop there once per item id and the result is cached. EDT-confined API.
 */
@Singleton
public class ItemInfoCache
{
	public static final class Info
	{
		public final String name;
		public final boolean stackable;

		Info(String name, boolean stackable)
		{
			this.name = name;
			this.stackable = stackable;
		}
	}

	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final Map<Integer, Info> cache = new HashMap<>();
	private final Map<Integer, List<Consumer<Info>>> pending = new HashMap<>();

	@Inject
	ItemInfoCache(ClientThread clientThread, ItemManager itemManager)
	{
		this.clientThread = clientThread;
		this.itemManager = itemManager;
	}

	/** Calls back on the EDT, immediately if the item is already cached. */
	public void lookup(int itemId, Consumer<Info> callback)
	{
		Info info = cache.get(itemId);
		if (info != null)
		{
			callback.accept(info);
			return;
		}
		List<Consumer<Info>> waiters = pending.get(itemId);
		if (waiters != null)
		{
			waiters.add(callback);
			return;
		}
		waiters = new ArrayList<>();
		waiters.add(callback);
		pending.put(itemId, waiters);

		clientThread.invokeLater(() ->
		{
			ItemComposition comp = itemManager.getItemComposition(itemId);
			Info result = new Info(comp.getName(), comp.isStackable());
			SwingUtilities.invokeLater(() ->
			{
				cache.put(itemId, result);
				List<Consumer<Info>> list = pending.remove(itemId);
				if (list != null)
				{
					list.forEach(c -> c.accept(result));
				}
			});
		});
	}
}
