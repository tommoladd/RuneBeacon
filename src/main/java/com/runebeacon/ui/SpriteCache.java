package com.runebeacon.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.game.SpriteManager;

/** Game sprites for the panel, loaded once and cached. EDT-confined API. */
@Singleton
public class SpriteCache
{
	private final SpriteManager spriteManager;
	private final Map<Integer, BufferedImage> cache = new HashMap<>();
	private final Map<Integer, List<Consumer<BufferedImage>>> pending = new HashMap<>();

	@Inject
	SpriteCache(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	/** @return the sprite if already loaded, otherwise null (and starts loading it). */
	public BufferedImage peek(int spriteId, Runnable onLoaded)
	{
		BufferedImage img = cache.get(spriteId);
		if (img == null)
		{
			get(spriteId, i -> onLoaded.run());
		}
		return img;
	}

	/** Calls back on the EDT, immediately if cached. */
	public void get(int spriteId, Consumer<BufferedImage> callback)
	{
		BufferedImage img = cache.get(spriteId);
		if (img != null)
		{
			callback.accept(img);
			return;
		}
		List<Consumer<BufferedImage>> waiters = pending.get(spriteId);
		if (waiters != null)
		{
			waiters.add(callback);
			return;
		}
		waiters = new ArrayList<>();
		waiters.add(callback);
		pending.put(spriteId, waiters);
		spriteManager.getSpriteAsync(spriteId, 0, loaded -> SwingUtilities.invokeLater(() ->
		{
			List<Consumer<BufferedImage>> list = pending.remove(spriteId);
			if (loaded == null)
			{
				return;
			}
			cache.put(spriteId, loaded);
			if (list != null)
			{
				list.forEach(c -> c.accept(loaded));
			}
		}));
	}
}
