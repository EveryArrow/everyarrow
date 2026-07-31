package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.util.Map;

/**
 * Result of REST route matching:
 *  - the matched ServiceCallDefinition
 *  - extracted path parameters (if any), e.g. {id} -> "123"
 */
public record RouteMatch(ServiceCallDefinition definition, Map<String, String> pathParams) {}
