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

package zhong.gg.mqtt.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhong.gg.mqtt.server.connect.ConnectServer;
import zhong.gg.mqtt.server.protocol.ConnectAction;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class MqttKeepAliveHandler extends IdleStateHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttKeepAliveHandler.class);

    private ConnectServer connectServer;
    private ConnectAction connectAction;

    public MqttKeepAliveHandler(ConnectServer connectServer, MqttConnectMessage msg, ConnectAction connectAction) {
        super((int) (msg.variableHeader().keepAliveTimeSeconds() * 1.5), 0, 0);
        log.info("MqttKeepAliveHandler init");
        this.connectServer = connectServer;
        this.connectAction = connectAction;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        log.info("channelIdle init, evt: {}", evt);
        connectAction.doDisconnect(ctx, connectServer);
    }
}
