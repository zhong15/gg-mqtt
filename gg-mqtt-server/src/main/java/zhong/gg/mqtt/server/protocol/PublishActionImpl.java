/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (ChannelHandlerContext ctx, MqttMessage msgthe "License");
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Singleton
public class PublishActionImpl implements PublishAction {
    private static final Logger log = LoggerFactory.getLogger(PublishActionImpl.class);

    private Receiver receiver;
    private Publisher publisher;

    @Inject
    public PublishActionImpl(Receiver receiver, Publisher publisher) {
        this.receiver = receiver;
        this.publisher = publisher;
    }

    @Override
    public Object onPubAck(ChannelHandlerContext ctx, MqttPubAckMessage msg) {
        return publisher.onPubAck(ctx, msg);
    }

    @Override
    public Object onPubRec(ChannelHandlerContext ctx, MqttMessage msg) {
        return publisher.onPubRec(ctx, msg);
    }

    @Override
    public Object onPubComp(ChannelHandlerContext ctx, MqttMessage msg) {
        return publisher.onPubComp(ctx, msg);
    }

    @Override
    public Object onPublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        return receiver.onPublish(ctx, msg);
    }

    @Override
    public Object onPubRel(ChannelHandlerContext ctx, MqttMessage msg) {
        return receiver.onPubRel(ctx, msg);
    }
}
