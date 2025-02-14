package com.github.steveice10.mc.protocol.packet.ingame.server.entity.player;

import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.NetOutput;
import com.github.steveice10.packetlib.packet.Packet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import java.io.IOException;

@Data
@With
@Setter(AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class ServerPlayerAbilitiesPacket implements Packet {
    private static final int FLAG_INVINCIBLE = 0x01;
    private static final int FLAG_FLYING = 0x02;
    private static final int FLAG_CAN_FLY = 0x04;
    private static final int FLAG_CREATIVE = 0x08;

    private boolean invincible;
    private boolean canFly;
    private boolean flying;
    private boolean creative;
    private float flySpeed;
    private float walkSpeed;

    @Override
    public void read(NetInput in) throws IOException {
        byte flags = in.readByte();
        this.invincible = (flags & FLAG_INVINCIBLE) > 0;
        this.canFly = (flags & FLAG_CAN_FLY) > 0;
        this.flying = (flags & FLAG_FLYING) > 0;
        this.creative = (flags & FLAG_CREATIVE) > 0;

        this.flySpeed = in.readFloat();
        this.walkSpeed = in.readFloat();
    }

    @Override
    public void write(NetOutput out) throws IOException {
        int flags = 0;
        if (this.invincible) {
            flags |= FLAG_INVINCIBLE;
        }

        if (this.canFly) {
            flags |= FLAG_CAN_FLY;
        }

        if (this.flying) {
            flags |= FLAG_FLYING;
        }

        if (this.creative) {
            flags |= FLAG_CREATIVE;
        }

        out.writeByte(flags);

        out.writeFloat(this.flySpeed);
        out.writeFloat(this.walkSpeed);
    }

    @Override
    public boolean isPriority() {
        return false;
    }
}
