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

package zhong.gg.mqtt.server.connect;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;

import java.util.Map;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface ConnectServer {
    void putChannel(String clientId, Channel channel);

    Channel removeChannel(String clientId);

    Channel getChannel(String clientId);

    void putConnectMessage(String clientId, MqttConnectMessage msg);

    MqttConnectMessage removeConnectMessage(String clientId);

    MqttConnectMessage getConnectMessage(String clientId);

    void putSession(String clientId, Session session);

    Session removeSession(String clientId);

    Session getSession(String clientId);

    boolean addSubscribe(String clientId, MqttSubscribeMessage msg);

    MqttSubscribeMessage removeSubscribe(String clientId, MqttUnsubscribeMessage msg);

    Map<String, MqttTopicSubscription> getSubscribeMap(String topicName);
}
