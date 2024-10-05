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
import zhong.gg.mqtt.server.utils.NamedThreadFactory;
import zhong.gg.mqtt.server.utils.PacketIdUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class ReceiverImpl implements Receiver, Service {
    private static final Logger log = LoggerFactory.getLogger(ReceiverImpl.class);

    private Broker broker;

    private ExecutorService executorService;

    private Map<Key, MqttPublishMessage> qos1PubAckFailureMap;
    private Map<Key, MqttPublishMessage> qos2PubRecSuccessMap;
    private Map<Key, MqttPublishMessage> qos2PubRecFailureMap;
    private Map<Key, MqttPublishMessage> qos2PubCompFailureMap;

    @Inject
    public ReceiverImpl(Broker broker) {
        this.broker = broker;

        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory("消息接收者线程"));

        this.qos1PubAckFailureMap = new ConcurrentHashMap<>();
        this.qos2PubRecSuccessMap = new ConcurrentHashMap<>();
        this.qos2PubRecFailureMap = new ConcurrentHashMap<>();
        this.qos2PubCompFailureMap = new ConcurrentHashMap<>();
    }

    @Override
    public Object onPublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        final int packetId = msg.variableHeader().packetId();
        log.debug("packetId: {}", packetId);
        switch (msg.fixedHeader().qosLevel()) {
            case AT_MOST_ONCE:
                broker.qos0Queue().offer(msg);
                break;
            case AT_LEAST_ONCE:
                if (!PacketIdUtils.isPacketId(packetId)) {
                    log.warn("is not a packetId: {}", packetId);
                    break;
                }
                boolean add = false;
                if (msg.fixedHeader().isDup()) {
                    add = addPacketId(ctx, packetId);
                } else {
                    addPacketId(ctx, packetId);
                    add = true;
                }
                if (!add) {
                    break;
                }
                broker.qos1Queue().offer(msg);
                Key key1 = new Key(getClientId(ctx), packetId);
                ChannelFuture future1 = ctx.channel().writeAndFlush(pubAckMessage(msg));
                future1.addListener(e -> {
                    if (e.isDone()) {
                        if (e.isSuccess()) {
                            removePacketId(ctx, packetId);
                        } else if (e.isCancelled()) {
                            qos1PubAckFailureMap.put(key1, msg);
                        } else {
                            qos1PubAckFailureMap.put(key1, msg);
                        }
                    }
                });
                break;
            case EXACTLY_ONCE:
                if (!PacketIdUtils.isPacketId(packetId)) {
                    log.warn("is not a packetId: {}", packetId);
                    break;
                }
                boolean put = false;
                if (msg.fixedHeader().isDup()) {
                    put = addPacketId(ctx, packetId);
                } else {
                    addPacketId(ctx, packetId);
                    put = true;
                }
                if (!put) {
                    break;
                }
                Key key2 = new Key(getClientId(ctx), packetId);
                ChannelFuture future2 = ctx.channel().writeAndFlush(pubRecMessage(msg));
                future2.addListener(e -> {
                    if (e.isDone()) {
                        if (e.isSuccess()) {
                            qos2PubRecSuccessMap.putIfAbsent(key2, msg);
                        } else if (e.isCancelled()) {
                            qos2PubRecFailureMap.putIfAbsent(key2, msg);
                        } else {
                            qos2PubRecFailureMap.putIfAbsent(key2, msg);
                        }
                    }
                });
                break;
            case FAILURE:
                break;
        }
        return null;
    }

    @Override
    public Object onPubRel(ChannelHandlerContext ctx, MqttMessage msg) {
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        log.debug("messageId: {}", messageId);
        Key key2 = new Key(getClientId(ctx), messageId);
        MqttPublishMessage publishMessage = qos2PubRecSuccessMap.remove(key2);
        if (publishMessage != null) {
            broker.qos2Queue().offer(publishMessage);
        } else {
            return null;
        }
        final int packetId = publishMessage.variableHeader().packetId();
        log.debug("packetId: {}", packetId);
        ChannelFuture future = ctx.channel().writeAndFlush(pubCompMessage(msg));
        future.addListener(e -> {
            if (e.isDone()) {
                if (e.isSuccess()) {
                    removePacketId(ctx, packetId);
                } else if (e.isCancelled()) {
                    qos2PubCompFailureMap.put(key2, publishMessage);
                } else {
                    qos2PubCompFailureMap.put(key2, publishMessage);
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

        // TODO: 失败重试
        executorService.submit(() -> {
            //pubAckFailureMap
            qos1PubAckFailureMap.forEach((k, v) -> {
                // TODO: 发 puback
                // TODO: 删 pubAckFailureMap
                // TODO: 删 packetID
            });
        });
        // 策略：网络请求只发一次成功即可，失败则由发起方重试
//        executorService.submit(() -> {
//            //pubRecSuccessMap
//            pubRecSuccessMap.forEach((k, v) -> {
//            });
//        });
        executorService.submit(() -> {
            //pubRecFailureMap
            qos2PubRecFailureMap.forEach((k, v) -> {
                // TODO: 发 pubrec
                // TODO: 删 pubRecFailureMap
                // TODO: 存 pubRecSuccessMap
            });
        });
        executorService.submit(() -> {
            //pubCompFailureMap
            qos2PubCompFailureMap.forEach((k, v) -> {
                // TODO: 发 pubcomp
                // TODO: 删 pubCompFailureMap
                // TODO: 删 packetId
            });
        });
    }

    @Override
    public void stop() {
        log.info("stop");
    }
}
