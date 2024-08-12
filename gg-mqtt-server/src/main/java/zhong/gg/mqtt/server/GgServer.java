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

package zhong.gg.mqtt.server;

import com.google.inject.*;
import com.google.inject.name.Names;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zhong.gg.mqtt.server.connect.ConnectServer;
import zhong.gg.mqtt.server.connect.Session;
import zhong.gg.mqtt.server.connect.auth.AuthModule;
import zhong.gg.mqtt.server.handler.HandlerModule;
import zhong.gg.mqtt.server.handler.ServerHandler;
import zhong.gg.mqtt.server.protocol.ProtocolModule;
import zhong.gg.mqtt.server.protocol.PublisherImpl;
import zhong.gg.mqtt.server.protocol.ReceiverImpl;
import zhong.gg.mqtt.server.utils.TopicUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class GgServer implements ConnectServer, Broker, PacketIdHolder {
    private static final Logger log = LoggerFactory.getLogger(GgServer.class);

    public static final int DEFAULT_SERVER_PORT = 8080;

    private static List<Service> serviceList;

    private int port;

    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private ServerHandler serverHandler;

    private Queue<MqttPublishMessage> qos0Queue;
    private Queue<MqttPublishMessage> qos1Queue;
    private Queue<MqttPublishMessage> qos2Queue;

    private ConcurrentMap<String, Integer> packetIdByClientIdMap;
    private ConcurrentMap<String, Set<Integer>> packetIdSetByClientIdMap;

    private ConcurrentMap<String, Channel> channelMap;
    private ConcurrentMap<String, MqttConnectMessage> connectMessageMap;
    private ConcurrentMap<String, Session> sessionMap;

    private ConcurrentMap<String, List<MqttTopicSubscription>> subTopicMap;

    @Inject
    public GgServer(ServerHandler serverHandler) {
        this.serverHandler = serverHandler;
    }

    public static void main(String[] args) {
//        new DefaultServer(DEFAULT_SERVER_PORT).run();
        Injector injector = Guice.createInjector(new ServerModule(),
                new AuthModule(),
                new HandlerModule(),
                new ProtocolModule());

        collectService(injector);

        injector.getInstance(GgServer.class)
                .run(DEFAULT_SERVER_PORT);
    }

    private static void collectService(Injector injector) {
        serviceList = new ArrayList<>();

        Service a = injector.getInstance(Key.get(Service.class, Names.named(PublisherImpl.class.getSimpleName())));
        serviceList.add(a);
        Service b = injector.getInstance(Key.get(Service.class, Names.named(ReceiverImpl.class.getSimpleName())));
        serviceList.add(b);
    }

    public void run(int port) {
        log.info("run");
        this.port = port;
        log.info("port: {}", this.port);
        try {
            init();

            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    .addLast(new MqttDecoder())
                                    .addLast(MqttEncoder.INSTANCE)
                                    .addLast(serverHandler);
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_REUSEADDR, true);
            ChannelFuture f = bootstrap.bind("127.0.0.1", port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.warn("interrupted", e);
        } finally {
            destroy();
        }
    }

    @Override
    public void init() {
        log.info("init");
        initServer();
        initBroker();
        initState();
        publishStartEvent();
    }

    private void publishStartEvent() {
        log.info("publishStartEvent");
        for (Service e : serviceList) {
            e.start();
        }
    }

    private void initServer() {
        log.info("init server");
        bootstrap = new ServerBootstrap();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
    }

    private void initBroker() {
        log.info("init broker");
        this.qos0Queue = new ConcurrentLinkedQueue<>();
        this.qos1Queue = new ConcurrentLinkedQueue<>();
        this.qos2Queue = new ConcurrentLinkedQueue<>();
    }

    private void initState() {
        log.info("init state");
        this.packetIdByClientIdMap = new ConcurrentHashMap<>();
        this.packetIdSetByClientIdMap = new ConcurrentHashMap<>();

        channelMap = new ConcurrentHashMap<>();
        connectMessageMap = new ConcurrentHashMap<>();
        sessionMap = new ConcurrentHashMap<>();
        subTopicMap = new ConcurrentHashMap<>();
    }

    @Override
    public void destroy() {
        log.info("destroy");
        log.info("work group shutdown");
        workerGroup.shutdownGracefully();
        log.info("boss group shutdown");
        bossGroup.shutdownGracefully();
        publishStopEvent();
    }

    private void publishStopEvent() {
        log.info("publishStopEvent");
        for (Service e : serviceList) {
            e.stop();
        }
    }

    @Override
    public void putChannel(String clientId, Channel channel) {
        channelMap.put(clientId, channel);
    }

    @Override
    public Channel removeChannel(String clientId) {
        return channelMap.remove(clientId);
    }

    @Override
    public Channel getChannel(String clientId) {
        return channelMap.get(clientId);
    }

    @Override
    public void putConnectMessage(String clientId, MqttConnectMessage msg) {
        connectMessageMap.put(clientId, msg);
    }

    @Override
    public MqttConnectMessage removeConnectMessage(String clientId) {
        return connectMessageMap.remove(clientId);
    }

    @Override
    public MqttConnectMessage getConnectMessage(String clientId) {
        return connectMessageMap.get(clientId);
    }

    @Override
    public void putSession(String clientId, Session session) {
        sessionMap.put(clientId, session);
    }

    @Override
    public Session removeSession(String clientId) {
        return sessionMap.remove(clientId);
    }

    @Override
    public Session getSession(String clientId) {
        return sessionMap.get(clientId);
    }

    @Override
    public boolean addSubscribe(String clientId, MqttSubscribeMessage msg) {
        if (msg.payload().topicSubscriptions() == null || msg.payload().topicSubscriptions().size() == 0) {
            return false;
        }

        for (MqttTopicSubscription e : msg.payload().topicSubscriptions()) {
            if (!TopicUtils.isTopic(e.topicName(), true)) {
                // TODO: 这里需要精细处理
                return false;
            }
        }

        List<MqttTopicSubscription> subTopicList = subTopicMap.get(clientId);
        if (subTopicList == null) {
            subTopicList = new ArrayList<>();
            subTopicMap.put(clientId, subTopicList);
        }
        subTopicList.addAll(msg.payload().topicSubscriptions());

        return true;
    }

    @Override
    public MqttSubscribeMessage removeSubscribe(String clientId, MqttUnsubscribeMessage msg) {
        if (msg.payload().topics() == null || msg.payload().topics().size() == 0) {
            return null;
        }

        for (String e : msg.payload().topics()) {
            if (!TopicUtils.isTopic(e, true)) {
                return null;
            }
        }

        List<MqttTopicSubscription> subTopicList = subTopicMap.get(clientId);
        if (subTopicList == null) {
            return null;
        }
        List<MqttTopicSubscription> newSubTopicList = new ArrayList<>();
        for (MqttTopicSubscription e : subTopicList) {
            if (!msg.payload().topics().contains(e.topicName())) {
                newSubTopicList.add(e);
            }
        }
        subTopicMap.put(clientId, newSubTopicList);

        // TODO: 返回值
        return null;
    }

    @Override
    public Map<String, MqttTopicSubscription> getSubscribeMap(String topicName) {
        final Map<String, MqttTopicSubscription> map = new HashMap<>();
        subTopicMap.forEach((k, v) -> {
            if (v == null || v.size() == 0) {
                return;
            }
            for (MqttTopicSubscription t : v) {
                boolean subTopic = TopicUtils.filter(t.topicName(), topicName);
                if (!subTopic) {
                    return;
                }
                map.put(k, t);
            }
        });
        return map;
    }

    @Override
    public Queue<MqttPublishMessage> qos0Queue() {
        return qos0Queue;
    }

    @Override
    public Queue<MqttPublishMessage> qos1Queue() {
        return qos1Queue;
    }

    @Override
    public Queue<MqttPublishMessage> qos2Queue() {
        return qos2Queue;
    }

    @Override
    public int incrementAndLockPacketId(String clientId) {
        // TODO: 这里需要考虑用尽 packet id 的情况
        Set<Integer> set = this.packetIdSetByClientIdMap.computeIfAbsent(clientId, k -> new HashSet<>());

        int packetId = this.packetIdByClientIdMap.computeIfAbsent(clientId, k -> 0);
        synchronized (set) {
            if (set.size() == GGConstant.MAX_PACKET_ID) {
                // TODO: packetId 用尽
            }
            do {
                packetId++;
                if (packetId > GGConstant.MAX_PACKET_ID) {
                    packetId = GGConstant.MIN_PACKET_ID;
                }
            } while (set.contains(packetId));
            set.add(packetId);
            this.packetIdByClientIdMap.put(clientId, packetId);
        }

        return packetId;
    }

    @Override
    public void unlockPacketId(String clientId, int packetId) {
        Set<Integer> set = this.packetIdSetByClientIdMap.get(clientId);
        if (set == null) {
            return;
        }
        set.remove(packetId);
    }
}
