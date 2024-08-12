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

package zhong.gg.mqtt.server.utils;

import java.util.Objects;

/**
 * @author Zhong
 * @since 0.0.1
 */
public class TopicUtils {
    private TopicUtils() {
    }

    public static boolean isTopic(String topic, boolean pattern) {
        if (topic == null) {
            return false;
        }
        if (topic.trim().length() == 0) {
            return false;
        }
        topic = topic.trim();
        // 不能以"/"开头
        if (topic.startsWith("/")) {
            return false;
        }
        // 不能以"/"结尾
        if (topic.endsWith("/")) {
            return false;
        }
        // 不能有连续"/"
        for (int i = 0; i < topic.length(); i++) {
            char c = topic.charAt(i);
            if (c != '/') {
                continue;
            }
            if (i > 0 && topic.charAt(i - 1) == '/') {
                return false;
            }
            if (i + 1 < topic.length() && topic.charAt(i + 1) == '/') {
                return false;
            }
        }
        if (pattern) {
            // 验证符号"+"
            if (topic.equals("+")) {
                return true;
            }
            for (int i = 0; i < topic.length(); i++) {
                char c = topic.charAt(i);
                if (c != '+') {
                    continue;
                }
                // 如果前面有字符，前一个字符必须是"/"
                if (i > 0 && topic.charAt(i - 1) != '/') {
                    return false;
                }
                // 如果后面有字符，后一个字符必须是"/"
                if (i + 1 < topic.length() && topic.charAt(i + 1) != '/') {
                    return false;
                }
            }
            // 验证符号"#"
            if (topic.equals("#")) {
                return true;
            }
            for (int i = 0; i < topic.length(); i++) {
                char c = topic.charAt(i);
                if (c != '#') {
                    continue;
                }
                // 必须是最后一个字符
                if (i != topic.length() - 1) {
                    return false;
                }
                // 前一个字符必须是"/"
                if (topic.charAt(i - 1) != '/') {
                    return false;
                }
            }
        } else {
            // 验证符号"+"
            if (topic.indexOf('+') != -1) {
                return false;
            }
            // 验证符号"#"
            if (topic.indexOf('#') != -1) {
                return false;
            }
        }
        return true;
    }

    public static boolean filter(String filter, String topic) {
        if (filter.indexOf('+') == -1 && filter.indexOf('#') == -1) {
            return Objects.equals(filter, topic);
        }
        // BF 算法 O(MN)
        String[] fs = filter.split("/");
        String[] ts = topic.split("/");
        for (int offset = 0; offset < fs.length; offset++) {
            for (int i = offset, j = 0; i < fs.length /*&& j < ts.length*/; i++, j++) {
                String f = fs[i];
                if (Objects.equals(f, "#")) {
                    return true;
                } else if (Objects.equals(f, "+") && j < ts.length) {
                    if (i == fs.length - 1 && j == ts.length - 1) {
                        return true;
                    } else {
                        continue;
                    }
                } else if (j < ts.length && Objects.equals(f, ts[j])) {
                    if (i == fs.length - 1 && j == ts.length - 1) {
                        return true;
                    } else {
                        continue;
                    }
                } else {
                    break;
                }
            }
        }
        return false;
    }
}
