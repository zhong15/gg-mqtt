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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import zhong.gg.mqtt.server.GGConstant;
import zhong.gg.mqtt.server.connect.ConnectServer;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class SubscribeActionImpl implements SubscribeAction {
    private ConnectServer connectServer;

    @Inject
    public SubscribeActionImpl(ConnectServer connectServer) {
        this.connectServer = connectServer;
    }

    @Override
    public Object onSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        String clientId = getClientId(ctx);
        boolean success = connectServer.addSubscribe(clientId, msg);
        ctx.channel().writeAndFlush(subAckMessage(msg, success));
        return null;
    }

    @Override
    public Object onSubAck(ChannelHandlerContext ctx, MqttSubAckMessage msg) {
        throw new UnsupportedOperationException("服务端不支持接收 subAck 数据包");
    }

    @Override
    public Object onUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        String clientId = getClientId(ctx);
        connectServer.removeSubscribe(clientId, msg);
        ctx.channel().writeAndFlush(unsubAckMessage(msg));
        return null;
    }

    @Override
    public Object onUnsubAck(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        throw new UnsupportedOperationException("服务端不支持接收 unsubAck 数据包");
    }

    private static MqttSubAckMessage subAckMessage(MqttSubscribeMessage subMsg, boolean success) {
        MqttMessageBuilders.SubAckBuilder builder = MqttMessageBuilders.subAck();
        for (int i = 0; i < subMsg.payload().topicSubscriptions().size(); i++) {
            builder.addGrantedQos(success ? GGConstant.MAX_QOS : MqttQoS.FAILURE);
        }
        return builder.build();
    }

    private static MqttUnsubAckMessage unsubAckMessage(MqttUnsubscribeMessage unsubMsg) {
        MqttMessageBuilders.UnsubAckBuilder builder = MqttMessageBuilders.unsubAck();
        return builder.build();
    }
}
