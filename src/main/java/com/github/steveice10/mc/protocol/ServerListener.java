package com.github.steveice10.mc.protocol;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.data.SubProtocol;
import com.github.steveice10.mc.protocol.data.status.PlayerInfo;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.data.status.VersionInfo;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoBuilder;
import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
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
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.*;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

public class ServerListener extends SessionAdapter {
    private static final KeyPair KEY_PAIR = CryptUtil.generateKeyPair();

    private byte verifyToken[] = new byte[4];
    private String serverId = "";
    private String username = "";

    private long lastPingTime = 0;
    private int lastPingId = 0;

    public ServerListener() {
        new Random().nextBytes(this.verifyToken);
    }

    @Override
    public void connected(ConnectedEvent event) {
        event.getSession().setFlag(MinecraftConstants.PING_KEY, 0);
    }

    @Override
    public void packetReceived(PacketReceivedEvent event) {
        var protocol = (MinecraftProtocol) event.getSession().getPacketProtocol();

        if (protocol.getSubProtocol() == SubProtocol.HANDSHAKE && event.getPacket() instanceof HandshakePacket packet)
            switch (packet.getIntent()) {
                case STATUS -> protocol.setSubProtocol(SubProtocol.STATUS, false);
                case LOGIN -> {
                    protocol.setSubProtocol(SubProtocol.LOGIN, false);
                    if (packet.getProtocolVersion() > MinecraftConstants.PROTOCOL_VERSION)
                        event.getSession().disconnect("Outdated server! I'm still on " + MinecraftConstants.GAME_VERSION + ".");
                    else if (packet.getProtocolVersion() < MinecraftConstants.PROTOCOL_VERSION)
                        event.getSession().disconnect("Outdated client! Please use " + MinecraftConstants.GAME_VERSION + ".");
                }
                default -> throw new UnsupportedOperationException("Invalid client intent: " + packet.getIntent());
            }

        if (protocol.getSubProtocol() == SubProtocol.LOGIN)
            if (event.getPacket() instanceof LoginStartPacket packet) {
                this.username = packet.getUsername();
                if (event.getSession().hasFlag(MinecraftConstants.VERIFY_USERS_KEY) && event.getSession().<Boolean>getFlag(MinecraftConstants.VERIFY_USERS_KEY))
                    event.getSession().send(new EncryptionRequestPacket(this.serverId, KEY_PAIR.getPublic(), this.verifyToken));
                else new Thread(new UserAuthTask(event.getSession(), null)).start();
            } else if (protocol.getSubProtocol() == SubProtocol.LOGIN && event.getPacket() instanceof EncryptionResponsePacket packet) {
                PrivateKey privateKey = KEY_PAIR.getPrivate();
                if (!Arrays.equals(this.verifyToken, packet.getVerifyToken(privateKey))) {
                    event.getSession().disconnect("Invalid nonce!");
                    return;
                } else {
                    SecretKey key = packet.getSecretKey(privateKey);
                    protocol.enableEncryption(key);
                    new Thread(new UserAuthTask(event.getSession(), key)).start();
                }
            }

        if (protocol.getSubProtocol() == SubProtocol.STATUS)
            if (event.getPacket() instanceof StatusQueryPacket) {
                ServerInfoBuilder builder = event.getSession().getFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY);
                if (builder == null)
                    builder = session -> new ServerStatusInfo(
                            VersionInfo.CURRENT, new PlayerInfo(0, 20, new GameProfile[]{}), "A Minecraft Server", null, true);
                event.getSession().send(new StatusResponsePacket(builder.buildInfo(event.getSession())));
            } else if (event.getPacket() instanceof StatusPingPacket packet)
                event.getSession().send(new StatusPongPacket(packet.getPingTime()));

        if (protocol.getSubProtocol() == SubProtocol.GAME && event.getPacket() instanceof ClientKeepAlivePacket packet)
            if (packet.getPingId() == this.lastPingId)
                event.getSession().setFlag(MinecraftConstants.PING_KEY, System.currentTimeMillis() - this.lastPingTime);
    }

    @Override
    public void packetSent(PacketSentEvent event) {
        var session = event.getSession();
        if (event.getPacket() instanceof LoginSetCompressionPacket packet) {
            session.setCompressionThreshold(packet.getThreshold());
            session.send(new LoginSuccessPacket(session.getFlag(MinecraftConstants.PROFILE_KEY)));
        } else if (event.getPacket() instanceof LoginSuccessPacket) {
            ((MinecraftProtocol) session.getPacketProtocol()).setSubProtocol(SubProtocol.GAME, false);
            ServerLoginHandler handler = session.getFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY);
            if (handler != null) handler.loggedIn(session);
            new Thread(new KeepAliveTask(session)).start();
        }
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {
        var protocol = (MinecraftProtocol) event.getSession().getPacketProtocol();
        boolean escape = false;
        if (event.getReason() != null)
            try {
                new JsonParser().parse(event.getReason());
            } catch (JsonSyntaxException ignored) {
                escape = true;
            }

        if (protocol.getSubProtocol() == SubProtocol.LOGIN)
            event.getSession().send(new LoginDisconnectPacket(event.getReason(), escape));
        else if (protocol.getSubProtocol() == SubProtocol.GAME)
            event.getSession().send(new ServerDisconnectPacket(event.getReason(), escape));
    }

    @RequiredArgsConstructor
    private class UserAuthTask implements Runnable {
        private final Session session;
        private final SecretKey key;

        @Override
        public void run() {
            boolean verify = this.session.hasFlag(MinecraftConstants.VERIFY_USERS_KEY) ? this.session.<Boolean>getFlag(MinecraftConstants.VERIFY_USERS_KEY) : true;

            GameProfile profile;
            if (verify && this.key != null) {

                try {
                    profile = new SessionService().getProfileByServer(username, new BigInteger(CryptUtil.getServerIdHash(serverId, KEY_PAIR.getPublic(), this.key)).toString(16));
                } catch (RequestException e) {
                    this.session.disconnect("Failed to make session service request.", e);
                    return;
                }

                if (profile == null) this.session.disconnect("Failed to verify username.");
            } else
                profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()), username);

            this.session.setFlag(MinecraftConstants.PROFILE_KEY, profile);
            this.session.send(new LoginSetCompressionPacket(this.session.hasFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD)
                    ? this.session.getFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD)
                    : 256));
        }
    }

    @RequiredArgsConstructor
    private class KeepAliveTask implements Runnable {
        private final Session session;

        @Override
        public void run() {
            while (this.session.isConnected()) {
                lastPingTime = System.currentTimeMillis();
                lastPingId = (int) lastPingTime;
                this.session.send(new ServerKeepAlivePacket(lastPingId));

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
