package com.github.steveice10.mc.protocol.util;

import com.github.steveice10.mc.protocol.data.MagicValues;
import com.github.steveice10.mc.protocol.data.game.chunk.BlockStorage;
import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.data.game.chunk.NibbleArray3d;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.*;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState;
import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.NetOutput;
import com.github.steveice10.packetlib.io.stream.StreamNetInput;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NetUtil {
    private static final int POSITION_X_SIZE = 38;
    private static final int POSITION_Y_SIZE = 26;
    private static final int POSITION_Z_SIZE = 38;
    private static final int POSITION_Y_SHIFT = 0xFFF;
    private static final int POSITION_WRITE_SHIFT = 0x3FFFFFF;

    public static CompoundTag readNBT(NetInput in) throws IOException {
        byte b = in.readByte();
        return b == 0 ? null : (CompoundTag) NBTIO.readTag(new NetInputStream(in, b));

    }

    public static void writeNBT(NetOutput out, CompoundTag tag) throws IOException {
        if (tag == null) out.writeByte(0);
        else NBTIO.writeTag(new NetOutputStream(out), tag);
    }

    public static BlockState readBlockState(NetInput in) throws IOException {
        int rawId = in.readVarInt();
        return new BlockState(rawId >> 4, rawId & 0xF);
    }

    public static void writeBlockState(NetOutput out, BlockState blockState) throws IOException {
        out.writeVarInt((blockState.getId() << 4) | (blockState.getData() & 0xF));
    }

    public static ItemStack readItem(NetInput in) throws IOException {
        short item = in.readShort();
        return item < 0 ? null : new ItemStack(item, in.readByte(), in.readShort(), readNBT(in));
    }

    public static void writeItem(NetOutput out, ItemStack item) throws IOException {
        if (item == null) out.writeShort(-1);
        else {
            out.writeShort(item.getId());
            out.writeByte(item.getAmount());
            out.writeShort(item.getData());
            writeNBT(out, item.getNBT());
        }
    }

    public static Position readPosition(NetInput in) throws IOException {
        long val = in.readLong();

        int x = (int) (val >> POSITION_X_SIZE);
        int y = (int) ((val >> POSITION_Y_SIZE) & POSITION_Y_SHIFT);
        int z = (int) ((val << POSITION_Z_SIZE) >> POSITION_Z_SIZE);

        return new Position(x, y, z);
    }

    public static void writePosition(NetOutput out, Position pos) throws IOException {
        long x = pos.getX() & POSITION_WRITE_SHIFT;
        long y = pos.getY() & POSITION_Y_SHIFT;
        long z = pos.getZ() & POSITION_WRITE_SHIFT;

        out.writeLong(x << POSITION_X_SIZE | y << POSITION_Y_SIZE | z);
    }

    public static Rotation readRotation(NetInput in) throws IOException {
        return new Rotation(in.readFloat(), in.readFloat(), in.readFloat());
    }

    public static void writeRotation(NetOutput out, Rotation rot) throws IOException {
        out.writeFloat(rot.getPitch());
        out.writeFloat(rot.getYaw());
        out.writeFloat(rot.getRoll());
    }

    public static EntityMetadata[] readEntityMetadata(NetInput in) throws IOException {
        var ret = new ArrayList<EntityMetadata>();
        int id;
        while ((id = in.readUnsignedByte()) != 255) {
            int typeId = in.readVarInt();
            var type = MagicValues.key(MetadataType.class, typeId);
            Object value = null;
            switch (type) {
                case BYTE -> value = in.readByte();
                case INT -> value = in.readVarInt();
                case FLOAT -> value = in.readFloat();
                case STRING, CHAT -> value = in.readString();
                case ITEM -> value = readItem(in);
                case BOOLEAN -> value = in.readBoolean();
                case ROTATION -> value = readRotation(in);
                case POSITION -> value = readPosition(in);
                case OPTIONAL_POSITION -> {
                    boolean positionPresent = in.readBoolean();
                    if (positionPresent) value = readPosition(in);
                }
                case BLOCK_FACE -> value = MagicValues.key(BlockFace.class, in.readVarInt());
                case OPTIONAL_UUID -> {
                    boolean uuidPresent = in.readBoolean();
                    if (uuidPresent) value = in.readUUID();
                }
                case BLOCK_STATE -> value = readBlockState(in);
                case NBT_TAG -> value = readNBT(in);
                default -> throw new IOException("Unknown metadata type id: " + typeId);
            }

            ret.add(new EntityMetadata(id, type, value));
        }

        return ret.toArray(new EntityMetadata[0]);
    }

    public static void writeEntityMetadata(NetOutput out, EntityMetadata[] metadata) throws IOException {
        for (var meta : metadata) {
            out.writeByte(meta.getId());
            out.writeVarInt(MagicValues.value(Integer.class, meta.getType()));
            switch (meta.getType()) {
                case BYTE -> out.writeByte((Byte) meta.getValue());
                case INT -> out.writeVarInt((Integer) meta.getValue());
                case FLOAT -> out.writeFloat((Float) meta.getValue());
                case STRING, CHAT -> out.writeString((String) meta.getValue());
                case ITEM -> writeItem(out, (ItemStack) meta.getValue());
                case BOOLEAN -> out.writeBoolean((Boolean) meta.getValue());
                case ROTATION -> writeRotation(out, (Rotation) meta.getValue());
                case POSITION -> writePosition(out, (Position) meta.getValue());
                case OPTIONAL_POSITION -> {
                    out.writeBoolean(meta.getValue() != null);
                    if (meta.getValue() != null) writePosition(out, (Position) meta.getValue());
                }
                case BLOCK_FACE -> out.writeVarInt(MagicValues.value(Integer.class, meta.getValue()));
                case OPTIONAL_UUID -> {
                    out.writeBoolean(meta.getValue() != null);
                    if (meta.getValue() != null) out.writeUUID((UUID) meta.getValue());
                }
                case BLOCK_STATE -> writeBlockState(out, (BlockState) meta.getValue());
                case NBT_TAG -> writeNBT(out, (CompoundTag) meta.getValue());
                default -> throw new IOException("Unknown metadata type: " + meta.getType());
            }
        }

        out.writeByte(255);
    }

    public static Column readColumn(byte[] data, int x, int z, boolean fullChunk, boolean hasSkylight, int mask, CompoundTag[] tileEntities) throws IOException {
        NetInput in = new StreamNetInput(new ByteArrayInputStream(data));
        Throwable ex = null;
        Column column = null;
        try {
            Chunk[] chunks = new Chunk[16];
            for (int index = 0; index < chunks.length; index++)
                if ((mask & (1 << index)) != 0) {
                    BlockStorage blocks = new BlockStorage(in);
                    NibbleArray3d blocklight = new NibbleArray3d(in, 2048);
                    NibbleArray3d skylight = hasSkylight ? new NibbleArray3d(in, 2048) : null;
                    chunks[index] = new Chunk(blocks, blocklight, skylight);
                }

            byte[] biomeData = null;
            if (fullChunk) biomeData = in.readBytes(256);

            column = new Column(x, z, chunks, biomeData, tileEntities);
        } catch (Throwable e) {
            ex = e;
        }

        // Unfortunately, this is needed to detect whether the chunks contain skylight or not.
        if ((in.available() > 0 || ex != null) && !hasSkylight) {
            return readColumn(data, x, z, fullChunk, true, mask, tileEntities);
        } else if (ex != null) {
            ex.printStackTrace(); // TTRMS
            throw new IOException("Failed to read chunk data.", ex);
        }

        return column;
    }

    public static int writeColumn(NetOutput out, Column column, boolean fullChunk, boolean hasSkylight) throws IOException {
        int mask = 0;
        Chunk[] chunks = column.getChunks();
        for (int index = 0; index < chunks.length; index++) {
            Chunk chunk = chunks[index];
            if (chunk != null && (!fullChunk || !chunk.isEmpty())) {
                mask |= 1 << index;
                chunk.getBlocks().write(out);
                chunk.getBlockLight().write(out);
                if (hasSkylight) chunk.getSkyLight().write(out);
            }
        }
        if (fullChunk) out.writeBytes(column.getBiomeData());
        return mask;
    }

    @RequiredArgsConstructor
    private static class NetInputStream extends InputStream {
        private final NetInput in;
        private final byte firstByte;
        private boolean readFirst;

        @Override
        public int read() throws IOException {
            if (!this.readFirst) {
                this.readFirst = true;
                return this.firstByte;
            } else return this.in.readUnsignedByte();
        }
    }

    @RequiredArgsConstructor
    private static class NetOutputStream extends OutputStream {
        private final NetOutput out;

        @Override
        public void write(int b) throws IOException {
            this.out.writeByte(b);
        }
    }
}
