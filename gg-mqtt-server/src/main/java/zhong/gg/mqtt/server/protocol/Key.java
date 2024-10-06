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

import java.util.Objects;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class Key implements Comparable<Key> {
    private final String clientId;
    private final int packetId;
    long retryTime;
    int retryCount;

    public Key(String clientId, int packetId) {
        if (clientId == null) {
            throw new NullPointerException("clientId is null");
        }
        this.clientId = clientId;
        this.packetId = packetId;
        this.retryTime = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Key key = (Key) o;
        return clientId.equals(key.clientId) && packetId == key.packetId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, packetId);
    }

    @Override
    public int compareTo(Key o) {
        if (o == null) {
            return 1;
        }
        // 先使用 packetId 比较可增加随机概率
        int first = Integer.compare(this.packetId, o.packetId);
        if (first != 0) {
            return first;
        }
        return this.clientId.compareTo(o.clientId);
    }
}
