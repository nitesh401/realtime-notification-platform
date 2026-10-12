package com.example.notify.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notify.api.domain.NotificationPreference;
import com.example.notify.common.Category;
import com.example.notify.common.Channel;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelResolverTest {

    private final ChannelResolver resolver = new ChannelResolver();
    private final Set<Channel> both = EnumSet.of(Channel.IN_APP, Channel.EMAIL);

    private NotificationPreference prefs() {
        return NotificationPreference.defaults(UUID.randomUUID());
    }

    @Test
    void defaultsAllowEverything() {
        assertThat(resolver.resolve(both, Category.INFO, false, prefs())).containsExactlyInAnyOrder(Channel.IN_APP, Channel.EMAIL);
    }

    @Test
    void disabledChannelIsRemoved() {
        NotificationPreference p = prefs();
        p.update(true, false, true, true, true, true);
        assertThat(resolver.resolve(both, Category.INFO, false, p)).containsExactly(Channel.IN_APP);
    }

    @Test
    void disabledCategorySuppressesEverything() {
        NotificationPreference p = prefs();
        p.update(true, true, false, true, true, true);
        assertThat(resolver.resolve(both, Category.INFO, false, p)).isEmpty();
        assertThat(resolver.resolve(both, Category.ERROR, false, p)).isNotEmpty();
    }

    @Test
    void mandatoryNotificationsIgnorePreferences() {
        NotificationPreference p = prefs();
        p.update(false, false, false, false, false, false);
        assertThat(resolver.resolve(both, Category.ERROR, true, p)).containsExactlyInAnyOrder(Channel.IN_APP, Channel.EMAIL);
    }
}
