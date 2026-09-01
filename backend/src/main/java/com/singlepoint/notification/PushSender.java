package com.singlepoint.notification;

import java.util.List;
import java.util.Map;

/** Delivers a push notification to a set of device tokens. */
public interface PushSender {

    /** @return true if the batch was accepted by the transport. */
    boolean send(List<String> deviceTokens, String title, String body, Map<String, Object> data);
}
