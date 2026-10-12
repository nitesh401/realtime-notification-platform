package com.example.notify.api.service;

import com.example.notify.api.domain.NotificationPreference;
import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies user preferences. Mandatory notifications (security / system) bypass preferences entirely; the
 * policy is explicit: only a template flagged mandatory, or an administrator, can create one.
 */
@Component
public class ChannelResolver {

    public Set<Channel> resolve(Set<Channel> requested, Category category, boolean mandatory, NotificationPreference prefs) {
        Set<Channel> result = requested.isEmpty() ? EnumSet.noneOf(Channel.class) : EnumSet.copyOf(requested);
        if (mandatory) {
            return result;
        }
        if (!prefs.isCategoryEnabled(category)) {
            return EnumSet.noneOf(Channel.class);
        }
        if (!prefs.isInAppEnabled()) {
            result.remove(Channel.IN_APP);
        }
        if (!prefs.isEmailEnabled()) {
            result.remove(Channel.EMAIL);
        }
        return result;
    }
}
