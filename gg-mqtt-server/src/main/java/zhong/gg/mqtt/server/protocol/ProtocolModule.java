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

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import zhong.gg.mqtt.server.Service;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class ProtocolModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConnectAction.class).to(ConnectActionImpl.class);
        bind(PingAction.class).to(PingActionImpl.class);
        bind(PublishAction.class).to(PublishActionImpl.class);
        bind(Publisher.class).to(PublisherImpl.class);
        bind(Service.class).annotatedWith(Names.named(PublisherImpl.class.getSimpleName())).to(PublisherImpl.class);
        bind(Receiver.class).to(ReceiverImpl.class);
        bind(Service.class).annotatedWith(Names.named(ReceiverImpl.class.getSimpleName())).to(ReceiverImpl.class);
        bind(SubscribeAction.class).to(SubscribeActionImpl.class);
    }
}
