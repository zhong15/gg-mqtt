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
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhong.gg.mqtt.server.Broker;
import zhong.gg.mqtt.server.GGConstant;
import zhong.gg.mqtt.server.PacketIdHolder;
import zhong.gg.mqtt.server.Service;
import zhong.gg.mqtt.server.connect.ConnectServer;
import zhong.gg.mqtt.server.utils.NamedThreadFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class PublisherImpl implements Publisher, Service {
    private static final Logger log = LoggerFactory.getLogger(PublisherImpl.class);

    private ConnectServer connectServer;
    private Broker broker;
    private PacketIdHolder packetIdHolder;

    private ExecutorService executorService;

    private Map<Key, MqttPublishMessage> qos0PublishFailureMap;
    private Map<Key, MqttPublishMessage> qos1PublishMap;
    private Map<Key, MqttPublishMessage> qos1PublishFailureMap;
    private Map<Key, MqttPublishMessage> qos2PublishMap;
    private Map<Key, MqttPublishMessage> qos2PublishFailureMap;
    private Set<Key> qos2PubRelSet;
    private Set<Key> qos2PubRelFailureSet;

    @Inject
    public PublisherImpl(ConnectServer connectServer, Broker broker, PacketIdHolder packetIdHolder) {
        this.connectServer = connectServer;
        this.broker = broker;
        this.packetIdHolder = packetIdHolder;

        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory("消息发布者线程池"));

        this.qos0PublishFailureMap = new ConcurrentHashMap<>();
        this.qos1PublishMap = new ConcurrentHashMap<>();
        this.qos1PublishFailureMap = new ConcurrentHashMap<>();
        this.qos2PublishMap = new ConcurrentHashMap<>();
        this.qos2PublishFailureMap = new ConcurrentHashMap<>();
        this.qos2PubRelSet = new ConcurrentSkipListSet<>();
        this.qos2PubRelFailureSet = new ConcurrentSkipListSet<>();
    }

    private void addPublishTask() {
        log.info("add publish task");

        log.info("add qos0 publish task");
        executorService.submit(() -> {
            for (; ; ) {
                MqttPublishMessage sourceMsg0 = broker.qos0Queue().poll();
                if (sourceMsg0 != null) {
                    publish(sourceMsg0, MqttQoS.AT_MOST_ONCE);
                    log.debug("sourceMsg0 refCnt: {}", sourceMsg0.refCnt());
                    sourceMsg0.release();
                    log.debug("sourceMsg0 release");
                }
            }
        });

        log.info("add qos1 publish task");
        executorService.submit(() -> {
            for (; ; ) {
                MqttPublishMessage sourceMsg1 = broker.qos1Queue().poll();
                if (sourceMsg1 != null) {
                    publish(sourceMsg1, MqttQoS.AT_LEAST_ONCE);
                    log.debug("sourceMsg1 refCnt: {}", sourceMsg1.refCnt());
                    sourceMsg1.release();
                    log.debug("sourceMsg1 release");
                }
            }
        });

        log.info("add qos2 publish task");
        executorService.submit(() -> {
            for (; ; ) {
                MqttPublishMessage sourceMsg2 = broker.qos2Queue().poll();
                if (sourceMsg2 != null) {
                    publish(sourceMsg2, MqttQoS.EXACTLY_ONCE);
                    log.debug("sourceMsg2 refCnt: {}", sourceMsg2.refCnt());
                    sourceMsg2.release();
                    log.debug("sourceMsg2 release");
                }
            }
        });
    }

    private void publish(MqttPublishMessage sourceMsg, MqttQoS mqttQoS) {
        Map<String, MqttTopicSubscription> map = connectServer.getSubscribeMap(sourceMsg.variableHeader().topicName());
        map.forEach((clientId, subscription) -> {
            Channel channel = connectServer.getChannel(clientId);
            if (channel == null) {
                return;
            }
            MqttQoS qos = MqttQoS.valueOf(Math.min(subscription.qualityOfService().value(), mqttQoS.value()));
            switch (qos) {
                case AT_MOST_ONCE:
                    log.debug("publish qos 0");
                    final int packetId0 = GGConstant.QOS0_PACKET_ID;
                    log.debug("packetId0: {}", packetId0);
                    final Key key0 = new Key(clientId, packetId0);
                    MqttPublishMessage msg0 = copyMsg(sourceMsg, qos, packetId0);
                    log.debug("msg0 refCnt: {}", msg0.refCnt());
                    msg0.retain();
                    ChannelFuture future0 = channel.writeAndFlush(msg0);
                    future0.addListener(f -> {
                        if (f.isDone()) {
                            if (f.isSuccess()) {
                                log.debug("msg0 refCnt: {}", msg0.refCnt());
                                msg0.release();
                                log.debug("msg0 release");
                            } else if (f.isCancelled()) {
                                qos0PublishFailureMap.put(key0, msg0);
                            } else {
                                qos0PublishFailureMap.put(key0, msg0);
                            }
                        }
                    });
                    break;
                case AT_LEAST_ONCE:
                    log.debug("publish qos 1");
                    final int packetId1 = packetIdHolder.incrementAndLockPacketId(clientId);
                    log.debug("packetId1: {}", packetId1);
                    final Key key1 = new Key(clientId, packetId1);
                    MqttPublishMessage msg1 = copyMsg(sourceMsg, qos, packetId1);
                    log.debug("msg1 refCnt: {}", msg1.refCnt());
                    msg1.retain();
                    qos1PublishMap.put(key1, msg1);
                    ChannelFuture future1 = channel.writeAndFlush(msg1);
                    future1.addListener(f -> {
                        if (f.isDone()) {
                            if (f.isSuccess()) {
                            } else if (f.isCancelled()) {
                                qos1PublishMap.remove(key1);
                                qos1PublishFailureMap.put(key1, msg1);
                            } else {
                                qos1PublishMap.remove(key1);
                                qos1PublishFailureMap.put(key1, msg1);
                            }
                        }
                    });
                    break;
                case EXACTLY_ONCE:
                    log.debug("publish qos 2");
                    final int packetId2 = packetIdHolder.incrementAndLockPacketId(clientId);
                    log.debug("packetId2: {}", packetId2);
                    final Key key2 = new Key(clientId, packetId2);
                    MqttPublishMessage msg2 = copyMsg(sourceMsg, qos, packetId2);
                    log.debug("msg2 refCnt: {}", msg2.refCnt());
                    msg2.retain();
                    qos2PublishMap.put(key2, msg2);
                    ChannelFuture future2 = channel.writeAndFlush(msg2);
                    future2.addListener(f -> {
                        if (f.isDone()) {
                            if (f.isSuccess()) {
                            } else if (f.isCancelled()) {
                                qos2PublishMap.remove(key2);
                                qos2PublishFailureMap.put(key2, msg2);
                            } else {
                                qos2PublishMap.remove(key2);
                                qos2PublishFailureMap.put(key2, msg2);
                            }
                        }
                    });
                    break;
                case FAILURE:
                    break;
            }
        });
    }

    private MqttPublishMessage copyMsg(MqttPublishMessage msg, MqttQoS qos, int packetId) {
        // TODO: 设置 isDup
        MqttFixedHeader fixedHeader = new MqttFixedHeader(msg.fixedHeader().messageType(), msg.fixedHeader().isDup(), qos, msg.fixedHeader().isRetain(), msg.fixedHeader().remainingLength());
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(msg.variableHeader().topicName(), packetId,//properties
                null);
        ByteBuf payload = msg.payload().copy();
        return new MqttPublishMessage(fixedHeader, variableHeader, payload);
    }

    @Override
    public Object onPubAck(ChannelHandlerContext ctx, MqttPubAckMessage msg) {
        String clientId = getClientId(ctx);
        final int messageId = msg.variableHeader().messageId();
        log.debug("messageId: {}", messageId);
        final Key key1 = new Key(clientId, messageId);
        MqttPublishMessage msg1 = qos1PublishMap.remove(key1);
        if (msg1 == null) {
            log.warn("msg1 null：已经 onPubAck");
            return null;
        }
        log.debug("msg1 refCnt: {}", msg1.refCnt());
        msg1.release();
        log.debug("msg1 release");
        packetIdHolder.unlockPacketId(clientId, messageId);
        return null;
    }

    @Override
    public Object onPubRec(ChannelHandlerContext ctx, MqttMessage msg) {
        String clientId = getClientId(ctx);
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        log.debug("messageId: {}", messageId);
        final Key key2 = new Key(clientId, messageId);
        MqttPublishMessage msg2 = qos2PublishMap.remove(key2);
        if (msg2 == null) {
            log.warn("msg2 null：已经 onPubRec");
            return null;
        }
        log.debug("msg2 refCnt: {}", msg2.refCnt());
        msg2.release();
        log.debug("msg2 release");
        qos2PubRelSet.add(key2);
        ChannelFuture future = ctx.channel().writeAndFlush(pubRelMessage(msg));
        future.addListener(f -> {
            if (f.isDone()) {
            } else if (f.isCancelled()) {
                qos2PubRelSet.remove(key2);
                qos2PubRelFailureSet.add(key2);
            } else {
                qos2PubRelSet.remove(key2);
                qos2PubRelFailureSet.add(key2);
            }
        });
        return null;
    }

    @Override
    public Object onPubComp(ChannelHandlerContext ctx, MqttMessage msg) {
        String clientId = getClientId(ctx);
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        log.debug("messageId: {}", messageId);
        boolean success = qos2PubRelSet.remove(new Key(clientId, messageId));
        if (!success) {
            log.warn("msg2 null：已经 onPubComp");
            return null;
        }
        packetIdHolder.unlockPacketId(clientId, messageId);
        return null;
    }

    private static MqttMessage pubRelMessage(MqttMessage msg) {
        final int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        log.debug("messageId: {}", messageId);
        //        MqttPubReplyMessageVariableHeader extends MqttMessageIdVariableHeader
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(messageId);
        return MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }

    @Override
    public void start() {
        log.info("start");
        addPublishTask();
        addRetryTask();
    }

    private void addRetryTask() {
        log.info("add retry task");

        log.info("add qos0 retry task");
        executorService.submit(() -> {
            //qos0PublishFailureMap
            // TODO: packetId 固定 GGConstant.QOS0_PACKET_ID
            // TODO: 设置 isDup
            // TODO: 先 msg0.retain()
            // TODO: publish
            // TODO: 删除 qos0PublishFailureMap
            // TODO: 成功后 msg0.release()
        });

        log.info("add qos1 retry task");
        executorService.submit(() -> {
            //qos1PublishMap
            // TODO: 设置 isDup
            // TODO: 先 msg1.retain()
            // TODO: publish
        });
        log.info("add qos1 retry task");
        executorService.submit(() -> {
            //qos1PublishFailureMap
            // TODO: 设置 isDup
            // TODO: 先 msg1.retain()
            // TODO: publish
            // TODO: 删除 qos1PublishFailureMap
            // TODO: 添加 qos1PublishMap
        });

        log.info("add qos2 retry task");
        executorService.submit(() -> {
            //qos2PublishMap
            // TODO: 设置 isDup
            // TODO: 先 msg2.retain()
            // TODO: publish
        });
        log.info("add qos2 retry task");
        executorService.submit(() -> {
            //qos2PublishFailureMap
            // TODO: 设置 isDup
            // TODO: 先 msg2.retain()
            // TODO: publish
            // TODO: 删除 qos2PublishFailureMap
            // TODO: 添加 qos2PublishMap
        });
        log.info("add qos2 retry task");
        executorService.submit(() -> {
            //qos2PubRelSet
            // TODO: 重新发送
        });
        log.info("add qos2 retry task");
        executorService.submit(() -> {
            //qos2PubRelFailureSet
            // TODO: 重新发送
            // TODO: 删除 qos2PubRelFailureSet
            // TODO: 添加 qos2PubRelSet
        });
    }

    @Override
    public void stop() {
        log.info("stop");
    }

}
