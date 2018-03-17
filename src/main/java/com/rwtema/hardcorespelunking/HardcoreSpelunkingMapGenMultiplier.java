package com.rwtema.hardcorespelunking;

import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.MapGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;

public class HardcoreSpelunkingMapGenMultiplier extends MapGenBase {
	final static EnumMap<InitMapGenEvent.EventType, Integer> GEN_MULTIPLIER_MAP = new EnumMap<>(InitMapGenEvent.EventType.class);
	private final MapGenBase base;
	private final int n;

	private HardcoreSpelunkingMapGenMultiplier(MapGenBase base, int n) {
		this.base = base;
		this.n = n;
	}

	static void init() {
		MinecraftForge.TERRAIN_GEN_BUS.register(HardcoreSpelunkingMapGenMultiplier.class);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void overwriteGen(InitMapGenEvent event) {
		@Nullable
		Integer integer = GEN_MULTIPLIER_MAP.get(event.getType());
		if (integer != null) {
			MapGenBase newGen = event.getNewGen();
			HardcoreSpelunkingMapGenMultiplier hardcoreSpelunkingMapGenMultiplier = new HardcoreSpelunkingMapGenMultiplier(newGen, integer);
			event.setNewGen(hardcoreSpelunkingMapGenMultiplier);
		}
	}

	@Override
	public void generate(World worldIn, int x, int z, @Nonnull ChunkPrimer primer) {
		for (int i = 0; i < n; i++) {
			base.generate(worldIn, x, z, primer);
			x = x * 31 + 20123;
			z = z * 31 + 21335;
		}
	}
}
