/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (ChannelHandlerContext ctx, MqttMessage msgthe "License");
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
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface SubscribeAction extends Action {
    Object onSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg);

    Object onSubAck(ChannelHandlerContext ctx, MqttSubAckMessage msg);

    Object onUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg);

    Object onUnsubAck(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg);
}
