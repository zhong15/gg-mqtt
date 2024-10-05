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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhong.gg.mqtt.server.protocol.ConnectAction;
import zhong.gg.mqtt.server.protocol.PingAction;
import zhong.gg.mqtt.server.protocol.PublishAction;
import zhong.gg.mqtt.server.protocol.SubscribeAction;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
@ChannelHandler.Sharable
public class ServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private ConnectAction connectAction;
    private PingAction pingAction;
    private PublishAction publishAction;
    private SubscribeAction subscribeAction;

    @Inject
    public ServerHandler(ConnectAction connectAction, PingAction pingAction,
                         PublishAction publishAction, SubscribeAction subscribeAction) {
        log.info("ServerHandler init");
        this.connectAction = connectAction;
        this.pingAction = pingAction;
        this.publishAction = publishAction;
        this.subscribeAction = subscribeAction;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.debug("channelRead msg: {}", msg);
        if (!(msg instanceof MqttMessage)) {
            log.warn("未知的 msg");
            ReferenceCountUtil.release(msg);
            return;
        }
        MqttMessageType msgType = ((MqttMessage) msg).fixedHeader().messageType();

        log.info("msgType: {}", msgType);

        switch (msgType) {
            case CONNECT:
                // client -> server
                connectAction.onConnect(ctx, (MqttConnectMessage) msg);
                break;
//                case CONNACK:
            // server -> client
//                    break;
            case PUBLISH:
                // 双向
                publishAction.onPublish(ctx, (MqttPublishMessage) msg);
                break;
            case PUBACK:
                // 双向
                // PUBLISH 1
                publishAction.onPubAck(ctx, (MqttPubAckMessage) msg);
                break;
            case PUBREC:
                // 双向
                // PUBLISH 2
                publishAction.onPubRec(ctx, (MqttMessage) msg);
                break;
            case PUBREL:
                // 双向
                // PUBLISH 2
                publishAction.onPubRel(ctx, (MqttMessage) msg);
                break;
            case PUBCOMP:
                // 双向
                // PUBLISH 2
                publishAction.onPubComp(ctx, (MqttMessage) msg);
                break;
            case SUBSCRIBE:
                // client -> server
                subscribeAction.onSubscribe(ctx, (MqttSubscribeMessage) msg);
                break;
//                case SUBACK:
            // server -> client
//                    break;
            case UNSUBSCRIBE:
                // client -> server
                subscribeAction.onUnsubscribe(ctx, (MqttUnsubscribeMessage) msg);
                break;
//                case UNSUBACK:
            // server -> client
//                    break;
            case PINGREQ:
                // client -> server
                pingAction.onPingReq(ctx, (MqttMessage) msg);
                break;
//                case PINGRESP:
            // server -> client
//                    break;
            case DISCONNECT:
//                client -> server
                connectAction.onDisconnect(ctx, (MqttMessage) msg);
                break;
//                case AUTH:
            // 双向
//                    break;
            default:
                break;
        }
    }
}
