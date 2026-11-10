package com.atlasglobal.shared.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching(order = org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "atlas.cache.enabled", havingValue = "true", matchIfMissing = true)
class QueryCacheConfiguration {}
