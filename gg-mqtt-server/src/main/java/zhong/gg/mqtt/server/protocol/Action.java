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

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import zhong.gg.mqtt.server.GGConstant;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface Action {
    default String getClientId(ChannelHandlerContext ctx) {
        Attribute<String> clientId = ctx.channel().attr(GGConstant.ATTR_CLIENT_ID);
        return clientId == null ? null : clientId.get();
    }

    default void setClientId(ChannelHandlerContext ctx, String clientId) {
        ctx.channel().attr(GGConstant.ATTR_CLIENT_ID).set(clientId);
    }
}
