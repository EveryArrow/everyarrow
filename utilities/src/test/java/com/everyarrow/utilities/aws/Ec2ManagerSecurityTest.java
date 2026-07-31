/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Ec2ManagerSecurityTest {

    @Test
    void rejectsPublicIngressForSensitivePorts() {
        assertThrows(IllegalArgumentException.class,
                () -> Ec2Manager.validateIngressRule(22, "0.0.0.0/0"));
        assertThrows(IllegalArgumentException.class,
                () -> Ec2Manager.validateIngressRule(5432, "0.0.0.0/0"));
        assertThrows(IllegalArgumentException.class,
                () -> Ec2Manager.validateIngressRule(2049, "::/0"));
    }

    @Test
    void allowsPublicIngressForPublicWebPortsOnly() {
        assertDoesNotThrow(() -> Ec2Manager.validateIngressRule(80, "0.0.0.0/0"));
        assertDoesNotThrow(() -> Ec2Manager.validateIngressRule(443, "0.0.0.0/0"));
    }
}
