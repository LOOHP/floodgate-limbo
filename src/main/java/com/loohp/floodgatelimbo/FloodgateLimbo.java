/*
 * This file is part of floodgate-limbo.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.loohp.floodgatelimbo;

import com.loohp.limbo.events.EventHandler;
import com.loohp.limbo.events.Listener;
import com.loohp.limbo.events.connection.ConnectionEstablishedEvent;
import com.loohp.limbo.network.ChannelPacketHandler;
import com.loohp.limbo.network.ChannelPacketRead;
import com.loohp.limbo.network.protocol.packets.PacketHandshakingIn;
import com.loohp.limbo.network.protocol.packets.PacketIn;
import com.loohp.limbo.plugins.LimboPlugin;
import net.kyori.adventure.key.Key;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public class FloodgateLimbo extends LimboPlugin implements Listener {

    private File keyFile;
    private AesCipher cipher;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        keyFile = new File(getDataFolder(), "key.pem");
        cipher = new AesCipher();
        try {
            cipher.init(new SecretKeySpec(Files.readAllBytes(keyFile.toPath()), "AES"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        getServer().getEventsManager().registerEvents(this, this);
    }

    @EventHandler
    public void onConnection(ConnectionEstablishedEvent event) {
        event.getConnection().getChannel().addHandlerAfter(Key.key("floodgate:handler"), new ChannelPacketHandler() {
            @Override
            public ChannelPacketRead read(ChannelPacketRead read) {
                PacketIn packetIn = read.getReadPacket();
                if (packetIn instanceof PacketHandshakingIn) {
                    PacketHandshakingIn handshaking = (PacketHandshakingIn) packetIn;
                    String serverAddress = handshaking.getServerAddress();

                    HostnameSeparationResult separation = separateHostname(serverAddress);
                    if (separation.floodgateData() != null) {
                        byte[] floodgateData = separation.floodgateData().getBytes(StandardCharsets.UTF_8);
                        try {
                            String decrypted = cipher.decryptToString(floodgateData);
                            BedrockData bedrockData = BedrockData.fromString(decrypted);

                            if (bedrockData.getXuid() != null) {
                                String[] split = separation.hostnameRemainder().split("\0");
                                if (split.length >= 3) {
                                    split[1] = bedrockData.getIp();
                                    split[2] = (bedrockData.hasPlayerLink() ? bedrockData.getLinkedPlayer().getJavaUniqueId().toString() : getJavaUuid(bedrockData.getXuid()).toString()).replace("-", "");
                                }
                                serverAddress = String.join("\0", split);
                                if (split.length == 3) {
                                    serverAddress += "\0[]";
                                }

                                read.setPacket(new PacketHandshakingIn(handshaking.getProtocolVersion(), serverAddress, handshaking.getServerPort(), handshaking.getHandshakeType()));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.read(read);
            }
        });
    }

    private static final int VERSION = 0;
    private static final byte[] IDENTIFIER = "^Floodgate^".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HEADER = (new String(IDENTIFIER, StandardCharsets.UTF_8) + (char) (VERSION + 0x3E)).getBytes(StandardCharsets.UTF_8);

    public static UUID getJavaUuid(long xuid) {
        return new UUID(0, xuid);
    }

    public static UUID getJavaUuid(String xuid) {
        return getJavaUuid(Long.parseLong(xuid));
    }

    private static int version(String data) {
        if (data.length() <= HEADER.length) {
            return -1;
        }

        for (int i = 0; i < IDENTIFIER.length; i++) {
            if (IDENTIFIER[i] != data.charAt(i)) {
                return -1;
            }
        }

        return data.charAt(IDENTIFIER.length) - 0x3E;
    }

    public HostnameSeparationResult separateHostname(String hostname) {
        String[] hostnameItems = hostname.split("\0");
        String floodgateData = null;
        int dataVersion = -1;

        StringBuilder builder = new StringBuilder();
        for (String value : hostnameItems) {
            int version = version(value);
            if (floodgateData == null && version != -1) {
                floodgateData = value;
                dataVersion = version;
                continue;
            }

            if (builder.length() > 0) {
                builder.append('\0');
            }
            builder.append(value);
        }
        // the new hostname doesn't have Floodgate data anymore, if it had Floodgate data.
        return new HostnameSeparationResult(floodgateData, dataVersion, builder.toString());
    }

    public static class HostnameSeparationResult {

        private final String floodgateData;
        private final int headerVersion;
        private final String hostnameRemainder;

        public HostnameSeparationResult(String floodgateData, int headerVersion, String hostnameRemainder) {
            this.floodgateData = floodgateData;
            this.headerVersion = headerVersion;
            this.hostnameRemainder = hostnameRemainder;
        }

        public String floodgateData() {
            return floodgateData;
        }

        public int headerVersion() {
            return headerVersion;
        }

        public String hostnameRemainder() {
            return hostnameRemainder;
        }
    }

}
