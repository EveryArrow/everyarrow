/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws.ses;

import java.util.concurrent.atomic.AtomicInteger;

public class DomainRotator {

    private final String[] fromAddresses;
    private final AtomicInteger index = new AtomicInteger(0);

    public DomainRotator(String[] fromAddresses) {
        if (fromAddresses == null || fromAddresses.length == 0)
            throw new IllegalArgumentException("At least one from-address is required.");
        this.fromAddresses = fromAddresses;
    }

    public String next() {
        int i = index.getAndUpdate(n -> (n + 1) % fromAddresses.length);
        return fromAddresses[i];
    }
}
