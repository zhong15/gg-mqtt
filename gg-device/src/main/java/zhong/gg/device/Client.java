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

package zhong.gg.device;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.*;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class Client {
    public static final int DEFAULT_SERVER_PORT = 8080;

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8080;
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap(); // (1)
            b.group(workerGroup); // (2)
            b.channel(NioSocketChannel.class); // (3)
            b.option(ChannelOption.SO_KEEPALIVE, true); // (4)
            b.option(ChannelOption.TCP_NODELAY, true); // (4)
            b.option(ChannelOption.SO_REUSEADDR, true); // (4)
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline()
                            .addLast(new MqttDecoder())
                            .addLast(MqttEncoder.INSTANCE)
                            .addLast(new ClientHandler());
                }
            });
            // Start the client.
            ChannelFuture f = b.connect(host, port).sync(); // (5)

            MqttMessage msg = connectMessage();

            f.channel().writeAndFlush(msg);
            // Wait until the connection is closed.
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    private static MqttMessage connectMessage() {
        return MqttMessageBuilders.connect()
                // variableHeader
                .protocolVersion(MqttVersion.MQTT_5)
                .hasUser(true)
                .hasPassword(true)
                .willRetain(false)
                .willQoS(MqttQoS.AT_LEAST_ONCE)
                .willFlag(false)
                .cleanSession(true)
                .keepAlive(10)
                // payload
                .clientId("/hello/mq")
                .willTopic("/hello/mqtt")
                .willMessage("willMessage".getBytes())
                .username("xiaoming")
                .password("pasword".getBytes())
                .build();
    }

    static MqttMessage publishMessage() {
        String s = "Hello, Mqtt!";
        ByteBuf buf = Unpooled.buffer(s.length());
        for (int i = 0; i < s.length(); i++)
            buf.writeChar(s.charAt(i));

        return MqttMessageBuilders.publish()
                .messageId(1)
                .retained(false)
                .topicName("haha")
                .qos(MqttQoS.AT_MOST_ONCE)
                .payload(buf)
                .build();
    }
}
