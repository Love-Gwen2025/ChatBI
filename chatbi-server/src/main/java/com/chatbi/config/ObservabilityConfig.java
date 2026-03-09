package com.chatbi.config;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class ObservabilityConfig {

    @Bean(destroyMethod = "close")
    public OpenTelemetrySdk openTelemetry(LangfuseProperties properties) {
        Resource resource = Resource.getDefault().merge(Resource.builder()
                .put("service.name", properties.getServiceName())
                .put("service.version", properties.getServiceVersion())
                .put("deployment.environment.name", properties.getEnvironment())
                .build());

        SdkTracerProviderBuilder providerBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        if (properties.isEnabled() && hasExporterConfig(properties)) {
            String endpoint = resolveOtlpEndpoint(properties);
            String authHeader = Base64.getEncoder().encodeToString(
                    (properties.getPublicKey() + ":" + properties.getSecretKey()).getBytes(StandardCharsets.UTF_8));

            OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .addHeader("Authorization", "Basic " + authHeader)
                    .build();

            providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
            log.info("Langfuse OTLP exporter enabled: {}", endpoint);
        } else if (properties.isEnabled()) {
            log.warn("Langfuse tracing is enabled but OTLP config is incomplete; spans stay local only");
        } else {
            log.info("Langfuse tracing exporter disabled; generating local trace ids only");
        }

        return OpenTelemetrySdk.builder()
                .setTracerProvider(providerBuilder.build())
                .build();
    }

    @Bean
    public Tracer chatBiTracer(OpenTelemetrySdk openTelemetrySdk, LangfuseProperties properties) {
        return openTelemetrySdk.getTracer(properties.getServiceName());
    }

    private boolean hasExporterConfig(LangfuseProperties properties) {
        return (hasText(properties.getOtlpEndpoint()) || hasText(properties.getHost()))
                && hasText(properties.getPublicKey())
                && hasText(properties.getSecretKey());
    }

    private String resolveOtlpEndpoint(LangfuseProperties properties) {
        if (hasText(properties.getOtlpEndpoint())) {
            return properties.getOtlpEndpoint().trim();
        }
        return properties.getHost().replaceAll("/+$", "") + "/api/public/otel/v1/traces";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
