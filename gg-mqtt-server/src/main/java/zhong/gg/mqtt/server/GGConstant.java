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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.AttributeKey;

import java.util.Set;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface GGConstant {
    AttributeKey<String> ATTR_CLIENT_ID = AttributeKey.valueOf("clientId");
    AttributeKey<Set<Integer>> ATTR_AT_LEAST_ONCE_PACKET_ID = AttributeKey.valueOf("packetId1");
    AttributeKey<Integer> ATTR_EXACTLY_ONCE_PACKET_ID = AttributeKey.valueOf("packetId2");
    // TODO: 应该读取配置文件
    MqttQoS MAX_QOS = MqttQoS.EXACTLY_ONCE;
    int MIN_PACKET_ID = 1;
    int MAX_PACKET_ID = (~(-1 << 16)) - 1;
    int retryTimes = 3;
    int pubAckRetryCount = 3;
    int pubRecRetryCount = 3;
    int pubCompRetryCount = 3;
}
