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
import zhong.gg.mqtt.server.Broker;
import zhong.gg.mqtt.server.PacketIdHolder;
import zhong.gg.mqtt.server.Service;
import zhong.gg.mqtt.server.connect.ConnectServer;
import zhong.gg.mqtt.server.utils.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private Map<Key, MqttPublishMessage> qos1PublishSuccessMap;
    private Map<Key, MqttPublishMessage> qos1PublishFailureMap;
    private Map<Key, MqttPublishMessage> qos2PublishSuccessMap;
    private Map<Key, MqttPublishMessage> qos2PublishFailureMap;
    private Map<Key, MqttPublishMessage> qos2PubRelSuccessMap;
    private Map<Key, MqttPublishMessage> qos2PubRelFailureMap;

    @Inject
    public PublisherImpl(ConnectServer connectServer, Broker broker, PacketIdHolder packetIdHolder) {
        this.connectServer = connectServer;
        this.broker = broker;
        this.packetIdHolder = packetIdHolder;

        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory("消息发布者线程池"));

        this.qos0PublishFailureMap = new ConcurrentHashMap<>();
        this.qos1PublishSuccessMap = new ConcurrentHashMap<>();
        this.qos1PublishFailureMap = new ConcurrentHashMap<>();
        this.qos2PublishSuccessMap = new ConcurrentHashMap<>();
        this.qos2PublishFailureMap = new ConcurrentHashMap<>();
        this.qos2PubRelSuccessMap = new ConcurrentHashMap<>();
        this.qos2PubRelFailureMap = new ConcurrentHashMap<>();
    }

    private void addPublishTask() {
        log.info("add publish task");

        executorService.submit(() -> {
            for (; ; ) {
                MqttPublishMessage msg = broker.qos0Queue().poll();
                if (msg != null) {
                    publish(msg, MqttQoS.AT_MOST_ONCE);
                }
            }
        });
        // qos0FailureMap
        executorService.submit(() -> {
            for (; ; ) {
                MqttPublishMessage msg = broker.qos1Queue().poll();
                if (msg != null) {
                    publish(msg, MqttQoS.AT_LEAST_ONCE);
                }
            }
        });
        // qos1Map
        // qos1FailureMap
        executorService.submit(() -> {
            for (; ; ) {
                MqttPublishMessage msg = broker.qos2Queue().poll();
                if (msg != null) {
                    publish(msg, MqttQoS.EXACTLY_ONCE);
                }
            }
        });
        // qos2Map
        // qos2FailureMap
        // qos2ReleaseMap
    }

    private void publish(MqttPublishMessage msg, MqttQoS mqttQoS) {
        Map<String, MqttTopicSubscription> map = connectServer.getSubscribeMap(msg.variableHeader().topicName());
        map.forEach((clientId, subscription) -> {
            Channel channel = connectServer.getChannel(clientId);
            if (channel == null) {
                return;
            }
            MqttQoS qos = MqttQoS.valueOf(Math.min(subscription.qualityOfService().value(), mqttQoS.value()));
            switch (qos) {
                case AT_MOST_ONCE:
                    // TODO: new msg packetId1 qos
                    final int packetId0 = packetIdHolder.incrementAndLockPacketId(clientId);
                    final Key key0 = new Key(clientId, packetId0);
                    ChannelFuture future0 = channel.writeAndFlush(msg);
                    future0.addListener(f -> {
                        if (f.isDone()) {
                            if (f.isSuccess()) {
                                packetIdHolder.unlockPacketId(clientId, packetId0);
                            } else if (f.isCancelled()) {
                                qos0PublishFailureMap.put(key0, msg);
                            } else {
                                qos0PublishFailureMap.put(key0, msg);
                            }
                        }
                    });
                    break;
                case AT_LEAST_ONCE:
                    // TODO: new msg packetId1 qos
                    final int packetId1 = packetIdHolder.incrementAndLockPacketId(clientId);
                    final Key key1 = new Key(clientId, packetId1);
                    ChannelFuture future1 = channel.writeAndFlush(msg);
                    future1.addListener(f -> {
                        if (f.isDone()) {
                            if (f.isSuccess()) {
                                qos1PublishSuccessMap.put(key1, msg);
                            } else if (f.isCancelled()) {
                                qos1PublishFailureMap.put(key1, msg);
                            } else {
                                qos1PublishFailureMap.put(key1, msg);
                            }
                        }
                    });
                    break;
                case EXACTLY_ONCE:
                    // TODO: new msg packetId1 qos
                    final int packetId2 = packetIdHolder.incrementAndLockPacketId(clientId);
                    final Key key2 = new Key(clientId, packetId2);
                    ChannelFuture future2 = channel.writeAndFlush(msg);
                    future2.addListener(f -> {
                        if (f.isDone()) {
                            if (f.isSuccess()) {
                                qos2PublishSuccessMap.put(key2, msg);
                            } else if (f.isCancelled()) {
                                qos2PublishFailureMap.put(key2, msg);
                            } else {
                                qos2PublishFailureMap.put(key2, msg);
                            }
                        }
                    });
                    break;
                case FAILURE:
                    break;
            }
        });
    }

    @Override
    public Object onPubAck(ChannelHandlerContext ctx, MqttPubAckMessage msg) {
        String clientId = getClientId(ctx);
        // TODO: packetId
        int packetId = msg.variableHeader().messageId();
        qos1PublishSuccessMap.remove(new Key(clientId, packetId));
        packetIdHolder.unlockPacketId(clientId, packetId);
        return null;
    }

    @Override
    public Object onPubRec(ChannelHandlerContext ctx, MqttMessage msg) {
        String clientId = getClientId(ctx);
        // TODO: packetId
        int packetId = 0;// msg.variableHeader().messageId();
        final Key key = new Key(clientId, packetId);
        MqttPublishMessage mqttPublishMessage = qos2PublishSuccessMap.remove(key);
        ChannelFuture future = ctx.channel().writeAndFlush(pubRelMessage(msg));
        future.addListener(f -> {
            if (f.isDone()) {
                qos2PubRelSuccessMap.put(key, mqttPublishMessage);
            } else if (f.isCancelled()) {
                qos2PubRelFailureMap.put(key, mqttPublishMessage);
            } else {
                qos2PubRelFailureMap.put(key, mqttPublishMessage);
            }
        });
        return null;
    }

    @Override
    public Object onPubComp(ChannelHandlerContext ctx, MqttMessage msg) {
        String clientId = getClientId(ctx);
        // TODO: packetId
        int packetId = 0;// msg.variableHeader().messageId();
        qos2PubRelSuccessMap.remove(new Key(clientId, packetId));
        packetIdHolder.unlockPacketId(clientId, packetId);
        return null;
    }

    private static MqttMessage pubRelMessage(MqttMessage msg) {
        //        MqttPubReplyMessageVariableHeader extends MqttMessageIdVariableHeader
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
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
        executorService.submit(() -> {
            //qos0PublishFailureMap
            // TODO: publish
            // TODO: 删除 qos0PublishFailureMap
        });

        executorService.submit(() -> {
            //qos1PublishSuccessMap
            // TODO: 重新发送
        });
        executorService.submit(() -> {
            //qos1PublishFailureMap
            // TODO: 重新发送
            // TODO: 删除 qos1PublishFailureMap
            // TODO: 添加 qos1PublishSuccessMap
        });

        executorService.submit(() -> {
            //qos2PublishSuccessMap
            // TODO: 重新发送
        });
        executorService.submit(() -> {
            //qos2PublishFailureMap
            // TODO: 重新发送
            // TODO: 删除 qos2PublishFailureMap
            // TODO: 添加 qos2PublishSuccessMap
        });
        executorService.submit(() -> {
            //qos2PubRelSuccessMap
            // TODO: 重新发送
        });
        executorService.submit(() -> {
            //qos2PubRelFailureMap
            // TODO: 重新发送
            // TODO: 删除 qos2PubRelFailureMap
            // TODO: 添加 qos2PubRelSuccessMap
        });
    }

    @Override
    public void stop() {
        log.info("stop");
    }

}
