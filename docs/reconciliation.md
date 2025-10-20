# Reconciliation Guide

This guide explains how speakToMack combines results from Vosk and Whisper into a single transcription using configurable strategies.

## Overview
When reconciliation is enabled, both engines run in parallel. The reconciler then selects the final text using one of several strategies. Metrics record the chosen strategy and selected engine.

Enable via properties:
```properties
stt.reconciliation.enabled=true
stt.reconciliation.strategy=simple      # simple | confidence | overlap
stt.reconciliation.overlap-threshold=0.6
```
Optional Whisper JSON tokens for improved overlap:
```properties
stt.whisper.output=json   # default is text mode
```

## Strategies
- **simple** (SimplePreferenceReconciler)
  - Picks the primary engine’s text unless it’s empty; otherwise picks the other engine
  - Use when you want predictable routing with a quick fallback

- **confidence** (ConfidenceReconciler)
  - Picks the result with higher confidence; tie break prefers non-empty text
  - Use when confidences are reasonably comparable

- **overlap** (WordOverlapReconciler)
  - Computes Jaccard similarity between each engine’s tokens and the union; picks higher similarity
  - Falls back to longer text if both overlaps below `overlap-threshold`
  - Benefits from Whisper JSON tokens (words/segments) when `stt.whisper.output=json`

## Tokenization
- Default tokenizer: lower-cased alpha tokens via `TokenizerUtil`
- JSON mode (Whisper): tokens derived from `segments[].words[].word`, falling back to segment text or top-level text; sanitized to alpha tokens

## Metrics
- `stt.reconcile.strategy_total{strategy=}`
- `stt.reconcile.selected_total{engine=vosk|whisper|unknown}`
- (Optional) disagreement counter when overlap < threshold

## Recommendations
- Start with `simple` or `confidence`
- If you observe frequent disagreements, try `overlap` and enable Whisper JSON (`stt.whisper.output=json`)
- Monitor metrics and INFO logs for durations and selected paths (no PII)

## Troubleshooting
- Both results empty: expect empty reconciled text; review audio validation thresholds
- Low overlap: verify tokenization and consider adjusting `stt.reconciliation.overlap-threshold`
- JSON parsing issues: keep `stt.whisper.output=text` (default) or verify your whisper.cpp build supports `-oj`
