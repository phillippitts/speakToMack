/**
 * Orchestration services for multi-engine STT coordination and reconciliation.
 *
 * <p>This package contains services that coordinate multiple STT engines for parallel
 * execution, fallback logic, and result reconciliation (Phase 4).
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator} -
 *       Orchestrates push-to-talk with dual-engine STT (Vosk + Whisper), automatic failover,
 *       and optional reconciliation-based transcription</li>
 * </ul>
 *
 * <p>Design Patterns:
 * <ul>
 *   <li><b>Event-Driven:</b> Uses Spring ApplicationEventPublisher for loose coupling
 *       (HotkeyPressed/Released → Transcription → TranscriptionCompleted)</li>
 *   <li><b>Strategy Pattern:</b> Pluggable reconciliation strategies (confidence, overlap, preference)</li>
 *   <li><b>Parallel Execution:</b> {@link com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService}
 *       runs both engines concurrently using CompletableFuture</li>
 *   <li><b>Fail-Safe:</b> Partial results when one engine fails, graceful degradation</li>
 *   <li><b>Conditional Configuration:</b> Reconciliation enabled via {@code stt.reconciliation.enabled}</li>
 * </ul>
 *
 * <p>Workflows:
 * <ul>
 *   <li><b>Single-Engine Mode (default):</b> Primary engine with automatic failover to secondary</li>
 *   <li><b>Reconciliation Mode (Phase 4):</b> Run both engines in parallel, reconcile results using
 *       configured strategy (SIMPLE, CONFIDENCE, or OVERLAP)</li>
 * </ul>
 *
 * <p>Phase 4 Reconciliation:
 * <ul>
 *   <li>SIMPLE: Always prefer primary engine (configurable Vosk or Whisper)</li>
 *   <li>CONFIDENCE: Select result with higher confidence score</li>
 *   <li>OVERLAP: Use Jaccard similarity to select result with better token overlap</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.service.stt.parallel
 * @see com.phillippitts.speaktomack.service.reconcile
 * @see com.phillippitts.speaktomack.config.reconcile
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.orchestration;
