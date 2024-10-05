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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhong.gg.mqtt.server.connect.ConnectServer;
import zhong.gg.mqtt.server.connect.GgSession;
import zhong.gg.mqtt.server.connect.Session;
import zhong.gg.mqtt.server.connect.auth.AuthService;
import zhong.gg.mqtt.server.handler.MqttKeepAliveHandler;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class ConnectActionImpl implements ConnectAction {
    private static final Logger log = LoggerFactory.getLogger(ConnectActionImpl.class);

    private ConnectServer connectServer;
    private AuthService authService;

    @Inject
    public ConnectActionImpl(ConnectServer connectServer, AuthService authService) {
        this.connectServer = connectServer;
        this.authService = authService;
    }

    @Override
    public Object onConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        log.info("[clientId={}]", clientId);

        /*
         * 只能发一次 CONNECT，如果发送多次，则关闭连接
         */
        Channel channel = connectServer.getChannel(clientId);
        if (channel != null) {
            log.info("连接已存在[clientId={}]", clientId);
//            doDisconnect(ctx, connectServer);
            ctx.close();
            return null;
        }

        /*
         * 登陆验证
         */
        boolean isAuthSuccess = false;
        if (msg.variableHeader().hasUserName() && msg.variableHeader().hasPassword()) {
            isAuthSuccess = authService.auth(msg.payload().userName(), msg.payload().passwordInBytes());
        } else if (msg.variableHeader().hasUserName()) {
            isAuthSuccess = authService.authUsername(msg.payload().userName());
        } else if (msg.variableHeader().hasPassword()) {
            isAuthSuccess = authService.authPassword(msg.payload().passwordInBytes());
        }

        MqttMessage connAck = connAckMessage(isAuthSuccess);
        ChannelFuture future = ctx.writeAndFlush(connAck);
        if (isAuthSuccess) {
            future.addListener(e -> {
                // 这里并发可能有问题，需要先锁定
                if (e.isSuccess()) {
                    setClientId(ctx, clientId);
                    connectServer.putChannel(clientId, ctx.channel());
                    connectServer.putConnectMessage(clientId, msg);

                    // clean session
                    if (msg.variableHeader().isCleanSession()) {
                        connectServer.removeSession(clientId);
                    }
                    if (connectServer.getSession(clientId) == null) {
                        Session session = new GgSession();
                        connectServer.putSession(clientId, session);
                    }

                    ctx.pipeline().addFirst(new MqttKeepAliveHandler(connectServer, msg, this));
                }
            });
        } else {
            future.addListener(e -> {
                if (e.isDone()) {
                    doDisconnectCore(ctx, connectServer, true);
                }
            });
        }
        return null;
    }

    @Override
    public Object onAuth(ChannelHandlerContext ctx, MqttMessage msg) {
        return null;
    }

    @Override
    public Object onConnAck(ChannelHandlerContext ctx, MqttConnAckMessage msg) {
        throw new UnsupportedOperationException("服务端不支持接收 connAck 数据包");
    }

    @Override
    public Object onDisconnect(ChannelHandlerContext ctx, MqttMessage msg) {
        log.info("断开连接");
        doDisconnect(ctx, connectServer);
        return null;
    }

    @Override
    public void doDisconnect(ChannelHandlerContext ctx, ConnectServer connectServer) {
        doDisconnectCore(ctx, connectServer, false);
    }

    private void doDisconnectCore(ChannelHandlerContext ctx, ConnectServer connectServer, boolean isAuthFailure) {
        log.info("断开连接");
        log.debug("isAuthFailure: {}", isAuthFailure);
        if (!isAuthFailure) {
            String clientId = getClientId(ctx);
            connectServer.removeChannel(clientId);
            // TODO: 处理 Session
            if (connectServer.getConnectMessage(clientId).variableHeader().isCleanSession()) {
                connectServer.removeSession(clientId);
            }
            // TODO: 处理 QoS 1/2 未确认的消息
            // TODO: 处理遗嘱消息
            if (connectServer.getConnectMessage(clientId).payload().willMessageInBytes() != null) {
            }
        }
        ctx.close();
        log.debug("ctx close");
    }

    private static MqttMessage connAckMessage(boolean isAuthSuccess) {
        final MqttConnectReturnCode returnCode = isAuthSuccess ? MqttConnectReturnCode.CONNECTION_ACCEPTED : MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
        return MqttMessageBuilders.connAck()
                .sessionPresent(true)
                .returnCode(returnCode)
                .build();
    }
}
