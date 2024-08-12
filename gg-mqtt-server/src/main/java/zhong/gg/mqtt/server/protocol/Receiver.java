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

package zhong.gg.mqtt.server.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.util.Attribute;
import zhong.gg.mqtt.server.GGConstant;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface Receiver extends Action {
    default boolean addPacketId(ChannelHandlerContext ctx, int packetId) {
        Set<Integer> set = ctx.channel().attr(GGConstant.ATTR_AT_LEAST_ONCE_PACKET_ID)
                .setIfAbsent(new ConcurrentSkipListSet<>());
        return set.add(packetId);
    }

    default boolean removePacketId(ChannelHandlerContext ctx, int packetId) {
        Attribute<Set<Integer>> attr = ctx.channel().attr(GGConstant.ATTR_AT_LEAST_ONCE_PACKET_ID);
        return attr.get() != null && attr.get().remove(packetId);
    }

    Object onPublish(ChannelHandlerContext ctx, MqttPublishMessage msg);

    Object onPubRel(ChannelHandlerContext ctx, MqttMessage msg);
}
