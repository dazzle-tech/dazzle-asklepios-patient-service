package com.dazzle.asklepios.integration.nahdi.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Nahdi skips an update of an order in status 1 or 9, sometimes answering success=true. */
class NahdiDoctorMessageLockedOrderTest {

    @Test
    void anOrderInStatus1IsBeingProcessed() {
        assertThat(NahdiDoctorMessage.ofLockedOrder("Order Stratus is 1, Order cannot changed"))
                .hasValueSatisfying(message -> {
                    assertThat(message.dataError()).isFalse();
                    assertThat(message.text()).contains("already processing");
                });
    }

    @Test
    void anOrderInStatus9WasDispensed() {
        assertThat(NahdiDoctorMessage.ofLockedOrder("Order Stratus is 9, copay Only allow to change"))
                .hasValueSatisfying(message -> assertThat(message.text()).contains("already dispensed"));
    }

    @Test
    void theCorrectSpellingIsAlsoRecognised() {
        assertThat(NahdiDoctorMessage.ofLockedOrder("order status is 9")).isPresent();
    }

    @Test
    void otherAnswersAreNotLockedOrders() {
        assertThat(NahdiDoctorMessage.ofLockedOrder("")).isEmpty();
        assertThat(NahdiDoctorMessage.ofLockedOrder(null)).isEmpty();
        assertThat(NahdiDoctorMessage.ofLockedOrder("Order Stratus is 3")).isEmpty();
        assertThat(NahdiDoctorMessage.ofLockedOrder("ER01- Invalid Provider ID or Token")).isEmpty();
    }
}
