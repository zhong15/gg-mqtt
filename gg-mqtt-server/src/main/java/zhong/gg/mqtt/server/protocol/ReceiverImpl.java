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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhong.gg.mqtt.server.Broker;
import zhong.gg.mqtt.server.Service;
import zhong.gg.mqtt.server.utils.PacketIdUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class ReceiverImpl implements Receiver, Service {
    private static final Logger log = LoggerFactory.getLogger(ReceiverImpl.class);

    private Broker broker;

    private Map<Key, MqttPublishMessage> qos2PubRecMap;

    @Inject
    public ReceiverImpl(Broker broker) {
        this.broker = broker;

        this.qos2PubRecMap = new ConcurrentHashMap<>();
    }

    @Override
    public Object onPublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        final int packetId = msg.variableHeader().packetId();
        log.debug("packetId: {}", packetId);
        switch (msg.fixedHeader().qosLevel()) {
            case AT_MOST_ONCE:
                broker.qos0Queue().offer(msg);
                return true;
            case AT_LEAST_ONCE:
                if (!PacketIdUtils.isPacketId(packetId)) {
                    log.warn("is not a packetId: {}", packetId);
                    msg.release();
                    return false;
                }
                boolean add = addPacketId(ctx, packetId);
                if (!add) {
                    msg.release();
                    return false;
                }
                broker.qos1Queue().offer(msg);
                ChannelFuture future1 = ctx.channel().writeAndFlush(pubAckMessage(msg));
                future1.addListener(e -> {
                    if (e.isDone()) {
                        removePacketId(ctx, packetId);
                        if (e.isSuccess()) {
                        } else if (e.isCancelled()) {
                        } else {
                        }
                    }
                });
                return true;
            case EXACTLY_ONCE:
                if (!PacketIdUtils.isPacketId(packetId)) {
                    log.warn("is not a packetId: {}", packetId);
                    msg.release();
                    return false;
                }
                boolean put = addPacketId(ctx, packetId);
                if (!put) {
                    msg.release();
                    return false;
                }
                Key key2 = new Key(getClientId(ctx), packetId);
                qos2PubRecMap.put(key2, msg);
                ChannelFuture future2 = ctx.channel().writeAndFlush(pubRecMessage(msg));
                future2.addListener(e -> {
                    if (e.isDone()) {
                        if (e.isSuccess()) {
                        } else if (e.isCancelled()) {
                            qos2PubRecMap.remove(key2);
                            removePacketId(ctx, packetId);
                        } else {
                            qos2PubRecMap.remove(key2);
                            removePacketId(ctx, packetId);
                        }
                    }
                });
                return true;
            case FAILURE:
            default:
                msg.release();
                return false;
        }
    }

    @Override
    public Object onPubRel(ChannelHandlerContext ctx, MqttMessage msg) {
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        log.debug("messageId: {}", messageId);
        Key key2 = new Key(getClientId(ctx), messageId);
        MqttPublishMessage msg2 = qos2PubRecMap.remove(key2);
        if (msg2 != null) {
            broker.qos2Queue().offer(msg2);
        } else {
            log.warn("msg2 is null：已经 onPubRel");
        }
        ChannelFuture future = ctx.channel().writeAndFlush(pubCompMessage(msg));
        future.addListener(e -> {
            if (e.isDone()) {
                if (msg2 != null) {
                    removePacketId(ctx, messageId);
                }
                if (e.isSuccess()) {
                } else if (e.isCancelled()) {
                } else {
                }
            }
        });
        return null;
    }

    private static MqttMessage pubAckMessage(MqttPublishMessage msg) {
        final int packetId = msg.variableHeader().packetId();
        log.debug("packetId: {}", packetId);

//        return MqttMessageBuilders.pubAck()
//                // MQTT 5.0 才支持 reasonCode
////                .reasonCode()
//                .packetId((short) packetId)
//                .build();

        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttPubReplyMessageVariableHeader mqttPubAckVariableHeader =
                new MqttPubReplyMessageVariableHeader(packetId, (byte) 0, null);
        return new MqttMessage(mqttFixedHeader, mqttPubAckVariableHeader);
    }

    private static MqttMessage pubRecMessage(MqttPublishMessage msg) {
        final int packetId = msg.variableHeader().packetId();
        log.debug("packetId: {}", packetId);
//        MqttPubReplyMessageVariableHeader extends MqttMessageIdVariableHeader
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(packetId);
        return MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }

    private static MqttMessage pubCompMessage(MqttMessage msg) {
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        log.debug("messageId: {}", messageId);
        //        MqttPubReplyMessageVariableHeader extends MqttMessageIdVariableHeader
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(messageId);
        return MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }

    @Override
    public void start() {
        log.info("start");
    }

    @Override
    public void stop() {
        log.info("stop");
    }
}
