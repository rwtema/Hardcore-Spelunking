package com.rwtema.stripminingprevention;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;

@Mod(modid = HardcoreSpelunkingMod.MODID, name = HardcoreSpelunkingMod.NAME, version = HardcoreSpelunkingMod.VERSION)
public class HardcoreSpelunkingMod {
	static final String MODID = "hardcorespelunking";
	static final String NAME = "Hardcore Spelunking";
	static final String VERSION = "1.0";
	private static final String BLOCK_REPLACEMENTS_RULES = "blockentries";
	private static final Pattern PATTERN = Pattern.compile("[^:]+:[^:]+:[0-9]+");
	private static final String DIMENSIONS_WHITELIST_BLACKLIST = "dimensions:whitelist/blacklist";
	private static final String DIMENSIONS = "dimensions";
	private static final String RESULT = "result";
	private static final String DISTANCE = "distance";
	private static final String TARGETBLOCKS = "targetblocks";
	private static final String TARGETOREDICTIONARY = "targetoredictionary";
	private static final String PRIORITY = "priority";
	private static final String OPTIONAL = "optional";
	private static final String SETTINGS_CATEGORY = "worldgenmultipliers";
	private static Logger logger;
	private static Configuration config;


	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		config = new Configuration(event.getSuggestedConfigurationFile());


		config.getCategory(SETTINGS_CATEGORY).setComment("Worldgen multipliers. Forces features to be re-run multiple times during gen. WARNING: Large values can increase world gen time significantly.");
		HardcoreSpelunkingMapGenMultiplier.GEN_MULTIPLIER_MAP.put(InitMapGenEvent.EventType.CAVE, config.getInt("cave_gen_multiplier", SETTINGS_CATEGORY, 1, 1, Integer.MAX_VALUE, "Forces cave generation to be run n times."));
		HardcoreSpelunkingMapGenMultiplier.GEN_MULTIPLIER_MAP.put(InitMapGenEvent.EventType.RAVINE, config.getInt("ravine_gen_multiplier", SETTINGS_CATEGORY, 1, 1, Integer.MAX_VALUE, "Forces ravine generation to be run n times."));
		HardcoreSpelunkingMapGenMultiplier.GEN_MULTIPLIER_MAP.put(InitMapGenEvent.EventType.NETHER_CAVE, config.getInt("nether_cave_gen_multiplier", SETTINGS_CATEGORY, 1, 1, Integer.MAX_VALUE, "Forces nether cave generation to be run n times."));
		HardcoreSpelunkingMapGenMultiplier.init();

	}

	@NetworkCheckHandler
	public boolean checkModLists(Map<String, String> modList, Side side) {
		return true;
	}

	@EventHandler
	public void postinit(FMLPostInitializationEvent event) {
		if (config.getCategoryNames().contains(BLOCK_REPLACEMENTS_RULES)) {
			generateDefaultConfig();
		}


		TreeMap<HardcoreSpelunkingWorldGenerator.Meta, HashMap<IBlockState, IBlockState>> stateRanges = new TreeMap<>(
				Comparator.<HardcoreSpelunkingWorldGenerator.Meta>comparingInt(m -> m.priority)
						.thenComparingInt(m -> m.distance)
		);

		for (String s : ((Iterable<String>) config.getCategory(BLOCK_REPLACEMENTS_RULES).getChildren().stream().map(ConfigCategory::getName)::iterator)) {
			String category = BLOCK_REPLACEMENTS_RULES + "." + s;
			String[] blockTargets = config.get(category, TARGETBLOCKS, new String[]{}, "Target Blocks/Meta", false, -1, PATTERN).getStringList();
			String[] oreTargets = config.get(category, TARGETOREDICTIONARY, new String[]{}, "Target OreDictionary values (uses RegEx)", false, -1, null).getStringList();
			String result = config.getString(RESULT, category, "", "Resulting block to change into", PATTERN);
			int distance = config.getInt(DISTANCE, category, 2, 1, 8, "Number of blocks from a non-suffocating block.");
			int priority = config.getInt(PRIORITY, category, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, "Priority that specifies execution order. In the event of ties, changes are made in terms of increasing distance number. Beyond that, execution order is undefined.");
			String[] dimensions = config.getStringList(DIMENSIONS, category, new String[]{}, "Dimension restriction. Can be an integer id, the name of the dimension type");
			String blacklist = config.getString(DIMENSIONS_WHITELIST_BLACKLIST, category, "Blacklist", "Dimension restriction type.", new String[]{"Whitelist", "Blacklist"});
			if (!"blacklist".equals(blacklist.toLowerCase(Locale.ENGLISH)) && !"whitelist".equals(blacklist.toLowerCase(Locale.ENGLISH))) {
				throw new RuntimeException("Unrecognized Whitelist/Blacklist value: " + blacklist);
			}

			boolean optional = config.getBoolean(OPTIONAL, category, false, "If the result block is missing, should we ignore?");
			HardcoreSpelunkingWorldGenerator.Meta meta = new HardcoreSpelunkingWorldGenerator.Meta(priority, distance, dimensions, blacklist);

			IBlockState resultState = getStateFromString(result);
			if (resultState == null) {
				if (!optional) {
					throw new RuntimeException("Block not found - " + result);
				}
				continue;
			}

			HashMap<IBlockState, IBlockState> states = stateRanges.computeIfAbsent(meta, integer -> new HashMap<>());
			for (String string : blockTargets) {
				IBlockState state = getStateFromString(string);

				if (state != null) {
					states.put(state, resultState);
				}
			}

			for (String string : oreTargets) {
				Pattern compilePattern = Pattern.compile(string);

				for (String oreName : OreDictionary.getOreNames()) {
					if (compilePattern.matcher(oreName).matches()) {
						for (ItemStack stack : OreDictionary.getOres(oreName)) {
							if (stack.getItem() instanceof ItemBlock) {
								Block block = ((ItemBlock) stack.getItem()).getBlock();
								if (stack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
									for (IBlockState state : block.getBlockState().getValidStates()) {
										states.put(state, resultState);
									}
								} else {
									int metadata = stack.getItem().getMetadata(stack.getMetadata());
									for (IBlockState state : block.getBlockState().getValidStates()) {
										if (block.getMetaFromState(state) == metadata) {
											states.put(state, resultState);
										}
									}
								}
							}
						}
					}
				}

			}
		}

		if (config.hasChanged()) {
			config.save();
		}

		List<HardcoreSpelunkingWorldGenerator> generators = new ArrayList<>();
		stateRanges.forEach((meta, iBlockStateIBlockStateHashMap) ->
				generators.add(new HardcoreSpelunkingWorldGenerator(meta, iBlockStateIBlockStateHashMap)));

		GameRegistry.registerWorldGenerator(new CombinedHardcoreSpelunkingWorldGenerator(generators), Integer.MAX_VALUE);
	}

	private void generateDefaultConfig() {
		config.getCategory(BLOCK_REPLACEMENTS_RULES).setComment("Block replacement rules. Add a new section to add new rules.");

		{
			String category = BLOCK_REPLACEMENTS_RULES + ".default_overworld";
			config.getStringList(TARGETBLOCKS, category, new String[]{
					"minecraft:stone:0",
					"minecraft:stone:1",
					"minecraft:stone:3",
					"minecraft:stone:5",
					"minecraft:dirt:0",
					"minecraft:gravel:0",
			}, "");
			config.getStringList(TARGETOREDICTIONARY, category, new String[]{
					"ore.+",
			}, "");
			config.getString(RESULT, category, "minecraft:bedrock:0", "");
			config.getInt(DISTANCE, category, 3, 1, 8, "");
			config.getStringList(DIMENSIONS, category, new String[]{
					"the_nether",
					"the_end"
			}, "");
			config.getString(DIMENSIONS_WHITELIST_BLACKLIST, category, "Blacklist", "", new String[]{"Whitelist", "Blacklist"});
		}

		{
			String category = BLOCK_REPLACEMENTS_RULES + ".default_nether";
			config.getStringList(TARGETBLOCKS, category, new String[]{
					"minecraft:netherrack:0",
					"minecraft:magma:0"
			}, "");
//				config.getStringList(TARGETOREDICTIONARY, category, new String[]{
//						"ore.+",
//				}, "");
			config.getString(RESULT, category, "minecraft:bedrock:0", "");
			config.getInt(DISTANCE, category, 2, 1, 8, "");
			config.getStringList(DIMENSIONS, category, new String[]{
					"the_nether"
			}, "");
			config.getString(DIMENSIONS_WHITELIST_BLACKLIST, category, "Whitelist", "", new String[]{"Whitelist", "Blacklist"});
		}
	}

	@Nullable
	@SuppressWarnings("deprecation")
	private IBlockState getStateFromString(String string) {
		if (!PATTERN.matcher(string).matches()) {
			throw new RuntimeException("Block entry not in correct format - ");
		}
		String[] split = string.trim().split(":");
		ResourceLocation location = new ResourceLocation(split[0], split[1]);
		if (Block.REGISTRY.containsKey(location)) {
			Block block = Block.REGISTRY.getObject(location);
			return block.getStateFromMeta(Integer.parseInt(split[2]));
		} else {
			logger.error("Unable to find block: " + location + " from " + string);
			return null;
		}
	}

	private static class CombinedHardcoreSpelunkingWorldGenerator implements IWorldGenerator {
		private final List<HardcoreSpelunkingWorldGenerator> generators;

		CombinedHardcoreSpelunkingWorldGenerator(List<HardcoreSpelunkingWorldGenerator> generators) {
			this.generators = generators;
		}

		@Override
		public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
			for (HardcoreSpelunkingWorldGenerator generator : generators) {
				generator.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
			}
		}
	}
}
