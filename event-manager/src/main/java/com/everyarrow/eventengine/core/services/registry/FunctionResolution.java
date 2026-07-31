/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic resolution result for one logical service type.
 */
public record FunctionResolution(
        String serviceType,
        FunctionCandidate selected,
        List<FunctionCandidate> candidates,
        String reason,
        FunctionOverrideEvidence overrideEvidence) {

    public FunctionResolution {
        Objects.requireNonNull(serviceType, "serviceType is required");
        Objects.requireNonNull(selected, "selected candidate is required");
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(reason, "resolution reason is required");
    }

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("serviceType", serviceType);
        out.addProperty("reason", reason);
        out.add("selected", selected.toJson(true));
        if (overrideEvidence != null) {
            out.add("overrideEvidence", overrideEvidence.toJson());
        }

        JsonArray candidateArray = new JsonArray();
        for (FunctionCandidate candidate : candidates) {
            candidateArray.add(candidate.toJson(candidate.equals(selected)));
        }
        out.add("candidates", candidateArray);
        return out;
    }
}
