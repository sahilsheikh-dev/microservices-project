package com.journal_service.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTests {
    @Test
    void health_rootRequest_returnsRunningStatus() {
        assertThat(new HealthController().health()).isEqualTo("Journal Service is running");
    }
}
