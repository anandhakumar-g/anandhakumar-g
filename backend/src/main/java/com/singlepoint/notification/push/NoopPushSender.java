package com.singlepoint.notification.push;

import com.singlepoint.notification.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** MVP-1 local/test push transport: logs instead of sending. */
@Component
@ConditionalOnProperty(name = "sp.push.provider", havingValue = "noop", matchIfMissing = true)
public class NoopPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoopPushSender.class);

    @Override
    public boolean send(List<String> deviceTokens, String title, String body, Map<String, Object> data) {
        log.info("PUSH (noop) -> {} device(s): [{}] {} data={}", deviceTokens.size(), title, body, data);
        return true;
    }
}
