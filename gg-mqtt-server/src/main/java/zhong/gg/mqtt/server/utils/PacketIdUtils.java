/*
 * Copyright 2024 the original author or authors.
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

package zhong.gg.mqtt.server.utils;

import zhong.gg.mqtt.server.GGConstant;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class PacketIdUtils {
    private PacketIdUtils() {
    }

    public static boolean isPacketId(int packetId) {
        return packetId >= GGConstant.MIN_PACKET_ID && packetId <= GGConstant.MAX_PACKET_ID;
    }
}
