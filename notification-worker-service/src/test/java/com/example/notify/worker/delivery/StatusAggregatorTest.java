package com.example.notify.worker.delivery;

import static com.example.notify.common.DeliveryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatusAggregatorTest {

    @Test void allDeliveredIsDelivered() { assertThat(StatusAggregator.aggregate(List.of(DELIVERED, DELIVERED))).isEqualTo(DELIVERED); }
    @Test void sentAndDeliveredIsSent() { assertThat(StatusAggregator.aggregate(List.of(SENT, DELIVERED))).isEqualTo(SENT); }
    @Test void anyRetryingWins() { assertThat(StatusAggregator.aggregate(List.of(SENT, RETRYING, DEAD_LETTERED))).isEqualTo(RETRYING); }
    @Test void deadLetteredBeatsSent() { assertThat(StatusAggregator.aggregate(List.of(SENT, DEAD_LETTERED))).isEqualTo(DEAD_LETTERED); }
    @Test void handoffInFlightIsProcessing() { assertThat(StatusAggregator.aggregate(List.of(PROCESSING, SENT))).isEqualTo(PROCESSING); }
    @Test void emptyIsPending() { assertThat(StatusAggregator.aggregate(List.of())).isEqualTo(PENDING); }
}
