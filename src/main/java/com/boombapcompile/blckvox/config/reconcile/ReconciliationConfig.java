package com.boombapcompile.blckvox.config.reconcile;

import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;

import com.boombapcompile.blckvox.config.properties.OrchestrationProperties;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.ConfidenceReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.SimplePreferenceReconciler;
import com.boombapcompile.blckvox.service.reconcile.impl.WordOverlapReconciler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationConfig {

    @Bean
    @ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")
    public TranscriptReconciler transcriptReconciler(ReconciliationProperties props,
                                                     OrchestrationProperties orchestrationProperties) {
        return switch (props.getStrategy()) {
            case SIMPLE -> new SimplePreferenceReconciler(orchestrationProperties.getPrimaryEngine());
            case CONFIDENCE -> new ConfidenceReconciler();
            case OVERLAP -> new WordOverlapReconciler(props.getOverlapThreshold());
        };
    }
}
