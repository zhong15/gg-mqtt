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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import zhong.gg.mqtt.server.connect.ConnectServer;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class PingActionImpl implements PingAction {
    private ConnectServer connectServer;

    @Inject
    public PingActionImpl(ConnectServer connectServer) {
        this.connectServer = connectServer;
    }

    @Override
    public Object onPingReq(ChannelHandlerContext ctx, MqttMessage msg) {
        msg = pingRespMessage();
        ctx.channel().writeAndFlush(msg);
        return null;
    }

    @Override
    public Object onPingResp(ChannelHandlerContext ctx, MqttMessage msg) {
        throw new UnsupportedOperationException("服务端不支持接收 pingResp 数据包");
    }

    private static MqttMessage pingRespMessage() {
        MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        return new MqttMessage(header);
    }
}
