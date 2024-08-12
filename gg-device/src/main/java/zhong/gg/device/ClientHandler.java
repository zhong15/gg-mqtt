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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MqttMessage) {
            MqttMessage mqttMessage = (MqttMessage) msg;
            MqttMessageType mqttMessageType = mqttMessage.fixedHeader().messageType();
            switch (mqttMessageType) {
//                case CONNECT:
//                    break;
                case CONNACK:
                    MqttConnAckMessage connAckMessage = (MqttConnAckMessage) msg;
                    if (connAckMessage.variableHeader().connectReturnCode() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                        log.info("连接成功");
                        ctx.writeAndFlush(Client.publishMessage());
                    } else {
                        log.info("连接失败");
                        ctx.close();
                    }
                    break;
                case PUBLISH:
                    // 双向
                    break;
                case PUBACK:
                    // 双向
                    break;
                case PUBREC:
                    // 双向
                    break;
                case PUBREL:
                    // 双向
                    break;
                case PUBCOMP:
                    // 双向
                    break;
//                case SUBSCRIBE:
//                    break;
                case SUBACK:
                    break;
//                case UNSUBSCRIBE:
//                    break;
                case UNSUBACK:
                    break;
//                case PINGREQ:
//                    break;
                case PINGRESP:
                    break;
                case DISCONNECT:
                    break;
                case AUTH:
                    break;
                default:
                    break;
            }
        }
    }
}
