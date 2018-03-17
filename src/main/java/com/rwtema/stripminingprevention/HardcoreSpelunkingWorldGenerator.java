package com.rwtema.stripminingprevention;

import com.google.common.collect.Lists;
import gnu.trove.set.hash.TIntHashSet;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.*;

public class HardcoreSpelunkingWorldGenerator implements IWorldGenerator {

	private final BlockPos[] offsetArray;
	private final BlockPos[] offsetArrayDownWasAir;
	private int n;
	private Meta meta;
	private Map<IBlockState, IBlockState> stoneTypes;
	private boolean[][][] array;

	HardcoreSpelunkingWorldGenerator(Meta meta, Map<IBlockState, IBlockState> stoneTypes) {
		this.n = meta.distance;
		this.meta = meta;
		this.stoneTypes = stoneTypes;

		ArrayList<BlockPos> offsets = new ArrayList<>();
		for (int x = -n; x <= n; x++) {
			for (int y = -n; y <= n; y++) {
				for (int z = -n; z <= n; z++) {
					int v = Math.abs(x) + Math.abs(y) + Math.abs(z);
					if (v <= n) {
						offsets.add(new BlockPos(x, y, z));
					}
				}
			}
		}
		this.offsetArray = offsets.stream().toArray(BlockPos[]::new);
		HashSet<BlockPos> altOffsets = new HashSet<>();
		altOffsets.addAll(offsets);
		offsets.stream().map(BlockPos::down).forEach(altOffsets::remove);
		offsetArrayDownWasAir = altOffsets.stream().toArray(BlockPos[]::new);


		array = new boolean[16][256][16];
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		if (!meta.matches(world)) return;

		Chunk[][] chunks = new Chunk[2][2];
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				chunks[i][j] = world.getChunkFromChunkCoords(chunkX + i, chunkZ + j);
			}
		}

		int topSegment = -1;
		mainLoop:
		for (int k = 15; k >= 0; k--) {
			for (int i = 0; i < 2; i++) {
				for (int j = 0; j < 2; j++) {
					if (chunks[i][j].getBlockStorageArray()[k] != null) {
						topSegment = k;
						break mainLoop;
					}
				}
			}
		}

		if (topSegment == -1) return;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				for (int k = 0; k <= topSegment; k++) {
					for (int dy = 0; dy < 16; dy++) {
						array[dx][k * 16 + dy][dz] = true;
					}
				}
			}
		}

		List<BlockPos> offsets = Lists.newArrayListWithExpectedSize(offsetArray.length);
		List<BlockPos> offsetsDownWasAir = Lists.newArrayListWithExpectedSize(offsetArray.length);
		for (int dx = 8 - n; dx < 24 + n; dx++) {
			for (int dz = 8 - n; dz < 24 + n; dz++) {
				offsets.clear();
				for (BlockPos offset : offsetArray) {
					int x = dx + offset.getX();
					if (x < 8 || x >= 24) continue;
					int z = dz + offset.getZ();
					if (z < 8 || z >= 24) continue;
					offsets.add(offset);
				}
				offsetsDownWasAir.clear();
				for (BlockPos offset : offsetArrayDownWasAir) {
					int x = dx + offset.getX();
					if (x < 8 || x >= 24) continue;
					int z = dz + offset.getZ();
					if (z < 8 || z >= 24) continue;
					offsetsDownWasAir.add(offset);
				}


				Chunk currentChunk = chunks[dx / 16][dz / 16];
				boolean downWasAir = false;
				for (int k = 0; k <= topSegment; k++) {
					ExtendedBlockStorage storage = currentChunk.getBlockStorageArray()[k];
					for (int dy = 0; dy < 16; dy++) {
						if (storage == null || isTraversable(storage.get(dx & 15, dy, dz & 15))) {
							for (BlockPos offset : downWasAir ? offsetsDownWasAir : offsets) {
								int y = k * 16 + dy + offset.getY();
								if (y < 0 || y >= 256) continue;
								int x = dx + offset.getX();
								int z = dz + offset.getZ();
								array[x - 8][y][z - 8] = false;
							}
							downWasAir = true;
						} else {
							downWasAir = false;
						}
					}
				}
			}
		}

		Map<IBlockState, IBlockState> stoneTypes = this.stoneTypes;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 8; dx < 24; dx++) {
			for (int dz = 8; dz < 24; dz++) {
				Chunk currentChunk = chunks[dx / 16][dz / 16];

				for (int k = 0; k <= topSegment; k++) {
					ExtendedBlockStorage storage = currentChunk.getBlockStorageArray()[k];
					if (storage != null) {
						for (int dy = 0; dy < 16; dy++) {
							if (array[dx - 8][k * 16 + dy][dz - 8]) {
								IBlockState state = storage.get(dx & 15, dy, dz & 15);
								IBlockState newState = stoneTypes.get(state);
								if (newState != null) {
									pos.setPos(chunks[0][0].x * 16 + dx, k * 16 + dy, chunks[0][0].z * 16 + dz);
									currentChunk.setBlockState(pos, newState);
								}
							}
						}
					}
				}
			}
		}
	}

	private boolean isTraversable(IBlockState state) {
		return !stoneTypes.containsKey(state) && !state.causesSuffocation() && state.getMaterial() != Material.LAVA;
	}

	public static class Meta {

		final int priority;
		final int distance;
		final String[] dimensions;
		final String blacklist;
		final TIntHashSet dimIDs = new TIntHashSet();
		final Set<String> dimNames = new HashSet<>();
		final boolean whitelist;

		public Meta(int priority, int distance, String[] dimensions, String blacklist) {

			this.priority = priority;
			this.distance = distance;
			this.dimensions = dimensions;
			this.blacklist = blacklist;
			whitelist = "whitelist".equals(blacklist.toLowerCase(Locale.ENGLISH));

			for (String dimension : dimensions) {
				try {
					int i = Integer.parseInt(dimension);
					dimIDs.add(i);
				} catch (NumberFormatException err) {
					dimNames.add(dimension);
				}
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Meta)) return false;

			Meta meta = (Meta) o;

			if (priority != meta.priority) return false;
			if (distance != meta.distance) return false;

			if (!Arrays.equals(dimensions, meta.dimensions)) return false;
			return blacklist.equals(meta.blacklist);
		}

		@Override
		public int hashCode() {
			int result = priority;
			result = 31 * result + distance;
			result = 31 * result + Arrays.hashCode(dimensions);
			result = 31 * result + blacklist.hashCode();
			return result;
		}

		public boolean matches(World world) {
			if (dimNames.contains(world.provider.getDimensionType().getName())) {
				return whitelist;
			}
			if (dimIDs.contains(world.provider.getDimension())) {
				return whitelist;
			}
			return !whitelist;
		}
	}
}


