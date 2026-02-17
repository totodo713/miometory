package com.worklog.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC configuration for static resources and API documentation.
 */
@Configuration
class WebConfig : WebMvcConfigurer {
    companion object {
        private const val CACHE_PERIOD_SECONDS = 3600
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Serve OpenAPI spec files
        registry
            .addResourceHandler("/api-docs/**")
            .addResourceLocations("classpath:/static/api-docs/")
            .setCachePeriod(CACHE_PERIOD_SECONDS)

        // Serve static files (including api-docs.html)
        registry
            .addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(CACHE_PERIOD_SECONDS)
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Redirect /docs to API documentation
        registry.addRedirectViewController("/docs", "/static/api-docs.html")
        registry.addRedirectViewController("/api-docs", "/static/api-docs.html")
    }
}
