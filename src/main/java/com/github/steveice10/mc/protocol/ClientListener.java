package com.github.steveice10.mc.protocol;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.exception.request.ServiceUnavailableException;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.handshake.HandshakeIntent;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoHandler;
import com.github.steveice10.mc.protocol.data.status.handler.ServerPingTimeHandler;
import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerSetCompressionPacket;
import com.github.steveice10.mc.protocol.packet.login.client.EncryptionResponsePacket;
import com.github.steveice10.mc.protocol.packet.login.client.LoginStartPacket;
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSetCompressionPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket;
import com.github.steveice10.mc.protocol.packet.status.client.StatusPingPacket;
import com.github.steveice10.mc.protocol.packet.status.client.StatusQueryPacket;
import com.github.steveice10.mc.protocol.packet.status.server.StatusPongPacket;
import com.github.steveice10.mc.protocol.packet.status.server.StatusResponsePacket;
import com.github.steveice10.mc.protocol.util.CryptUtil;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.PacketSentEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.math.BigInteger;

@AllArgsConstructor
public class ClientListener extends SessionAdapter {
    private final @NonNull SubProtocol targetSubProtocol;

    @Override
    public void packetReceived(PacketReceivedEvent event) {
        var protocol = (MinecraftProtocol) event.getSession().getPacketProtocol();

        if (protocol.getSubProtocol() == SubProtocol.LOGIN) {
            if (event.getPacket() instanceof EncryptionRequestPacket packet) {
                var key = CryptUtil.generateSharedKey();
                GameProfile profile = event.getSession().getFlag(MinecraftConstants.PROFILE_KEY);
                String serverHash = new BigInteger(CryptUtil.getServerIdHash(packet.getServerId(), packet.getPublicKey(), key)).toString(16);
                String accessToken = event.getSession().getFlag(MinecraftConstants.ACCESS_TOKEN_KEY);

                try {
                    new SessionService().joinServer(profile, accessToken, serverHash);
                } catch (ServiceUnavailableException e) {
                    event.getSession().disconnect("Login failed: Authentication service unavailable.", e);
                    return;
                } catch (InvalidCredentialsException e) {
                    event.getSession().disconnect("Login failed: Invalid login session.", e);
                    return;
                } catch (RequestException e) {
                    event.getSession().disconnect("Login failed: Authentication error: " + e.getMessage(), e);
                    return;
                }

                event.getSession().send(new EncryptionResponsePacket(key, packet.getPublicKey(), packet.getVerifyToken()));
                protocol.enableEncryption(key);
            } else if (event.getPacket() instanceof LoginSuccessPacket packet) {
                event.getSession().setFlag(MinecraftConstants.PROFILE_KEY, packet.getProfile());
                protocol.setSubProtocol(SubProtocol.GAME, true);
            } else if (event.getPacket() instanceof LoginDisconnectPacket packet)
                event.getSession().disconnect(packet.getReason());
            else if (event.getPacket() instanceof LoginSetCompressionPacket packet)
                event.getSession().setCompressionThreshold(packet.getThreshold());
        } else if (protocol.getSubProtocol() == SubProtocol.STATUS) {
            if (event.getPacket() instanceof StatusResponsePacket packet) {
                ServerInfoHandler handler = event.getSession().getFlag(MinecraftConstants.SERVER_INFO_HANDLER_KEY);
                if (handler != null)
                    handler.handle(event.getSession(), packet.getInfo());
                event.getSession().send(new StatusPingPacket(System.currentTimeMillis()));
            } else if (event.getPacket() instanceof StatusPongPacket packet) {
                ServerPingTimeHandler handler = event.getSession().getFlag(MinecraftConstants.SERVER_PING_TIME_HANDLER_KEY);
                if (handler != null)
                    handler.handle(event.getSession(), System.currentTimeMillis() - packet.getPingTime());
                event.getSession().disconnect("Finished");
            }
        } else if (protocol.getSubProtocol() == SubProtocol.GAME) {
            if (event.getPacket() instanceof ServerKeepAlivePacket packet)
                event.getSession().send(new ClientKeepAlivePacket(packet.getPingId()));
            else if (event.getPacket() instanceof ServerDisconnectPacket packet)
                event.getSession().disconnect(packet.getReason());
            else if (event.getPacket() instanceof ServerSetCompressionPacket packet)
                event.getSession().setCompressionThreshold(packet.getThreshold());
        }
    }

    @Override
    public void packetSent(PacketSentEvent event) {
        if (event.getPacket() instanceof HandshakePacket) {
            // Once the HandshakePacket has been sent, switch to the next protocol mode.
            ((MinecraftProtocol) event.getSession().getPacketProtocol()).setSubProtocol(this.targetSubProtocol, true);

            if (this.targetSubProtocol == SubProtocol.LOGIN) {
                GameProfile profile = event.getSession().getFlag(MinecraftConstants.PROFILE_KEY);
                event.getSession().send(new LoginStartPacket(profile != null ? profile.getName() : ""));
            } else event.getSession().send(new StatusQueryPacket());
        }
    }

    @Override
    public void connected(ConnectedEvent event) {
        if (this.targetSubProtocol == SubProtocol.LOGIN || this.targetSubProtocol == SubProtocol.STATUS)
            event.getSession().send(new HandshakePacket(
                    MinecraftConstants.PROTOCOL_VERSION,
                    event.getSession().getHost(),
                    event.getSession().getPort(),
                    this.targetSubProtocol == SubProtocol.LOGIN ? HandshakeIntent.LOGIN : HandshakeIntent.STATUS));
    }
}
