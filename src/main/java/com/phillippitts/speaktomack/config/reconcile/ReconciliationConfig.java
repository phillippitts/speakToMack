package com.phillippitts.speaktomack.config.reconcile;

import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.ConfidenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.SimplePreferenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler;
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
