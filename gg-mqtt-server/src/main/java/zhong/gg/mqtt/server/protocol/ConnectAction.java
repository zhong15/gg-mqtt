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
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import zhong.gg.mqtt.server.connect.ConnectServer;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface ConnectAction {
    Object onConnect(ChannelHandlerContext ctx, MqttConnectMessage msg);

    Object onAuth(ChannelHandlerContext ctx, MqttMessage msg);

    Object onConnAck(ChannelHandlerContext ctx, MqttConnAckMessage msg);

    Object onDisconnect(ChannelHandlerContext ctx, MqttMessage msg);

    void doDisconnect(ChannelHandlerContext ctx, ConnectServer connectServer);
}
