package de.theboys.block;

import de.theboys.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block MiniMaus nibbled on: the original block, split into 16x16x16 pixels, some of them gone.
 * The shape you walk on and aim at is exactly the pixels that are left.
 */
public class CarvedBlockEntity extends BlockEntity {
	public static final int SIZE = 16;
	public static final int COUNT = SIZE * SIZE * SIZE;

	private BlockState original = Blocks.STONE.defaultBlockState();
	/** 1 = pixel still there. Index x + 16 * (y + 16 * z). */
	private final int[] bits = new int[COUNT / 32];
	private VoxelShape shape;
	/** bumped on every change so the client rebuilds its mesh */
	public int version;
	/** client only: the cached pixel mesh */
	public Object clientMesh;

	public CarvedBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CARVED_TYPE, pos, state);
		java.util.Arrays.fill(bits, -1);
	}

	public void init(BlockState original) {
		this.original = original;
		java.util.Arrays.fill(bits, -1);
		changed();
	}

	public BlockState original() {
		return original;
	}

	public static int index(int x, int y, int z) {
		return x + SIZE * (y + SIZE * z);
	}

	public boolean filled(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE || z >= SIZE) return false;
		int i = index(x, y, z);
		return (bits[i >>> 5] & (1 << (i & 31))) != 0;
	}

	public void remove(int x, int y, int z) {
		int i = index(x, y, z);
		bits[i >>> 5] &= ~(1 << (i & 31));
		changed();
	}

	public int remaining() {
		int n = 0;
		for (int b : bits) n += Integer.bitCount(b);
		return n;
	}

	public boolean isEmpty() {
		for (int b : bits) if (b != 0) return false;
		return true;
	}

	private void changed() {
		shape = null;
		version++;
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	/** The exact pixel shape. */
	public VoxelShape shape() {
		if (shape == null) {
			if (isEmpty()) {
				shape = Shapes.empty();
			} else {
				BitSetDiscreteVoxelShape d = new BitSetDiscreteVoxelShape(SIZE, SIZE, SIZE);
				for (int z = 0; z < SIZE; z++) for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
					if (filled(x, y, z)) d.fill(x, y, z);
				}
				shape = new CubeVoxelShape(d);
			}
		}
		return shape;
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.store("original", BlockState.CODEC, original);
		out.putIntArray("pixels", bits.clone());
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		original = in.read("original", BlockState.CODEC).orElse(Blocks.STONE.defaultBlockState());
		in.getIntArray("pixels").ifPresent(a -> System.arraycopy(a, 0, bits, 0, Math.min(a.length, bits.length)));
		shape = null;
		version++;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
