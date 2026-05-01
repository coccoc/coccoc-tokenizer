# Java Standalone Module

A pure-Java implementation of the CocCoc Vietnamese tokenizer is now available as a Maven artifact under `java/`. It does not require any native libraries or a C++ build.

## Features

* **Classpath dict loading** — dictionary files are bundled inside the `coccoc-tokenizer-java-dicts` jar and loaded automatically via `Tokenizer.getInstance()`. No external file path needed.
* **Filesystem dict loading** — `Tokenizer.getInstance(String dictPath)` loads `multiterm.bin`, `syllable.bin`, and the optional `bigram.bin` from a directory on disk, matching the behaviour of the C++ library.
* **Full segmentation modes** — `NORMAL`, `HOST`, and `URL` modes match the C++ tokenizer's output.
* **keepPunct filtering** — the `keepPunctuation` flag (and the `segmentKeepPuncts*` convenience methods) mirrors the `-k` option in the CLI tool.
* **Thread safety** — the `Tokenizer` singleton is safe to call concurrently from multiple threads.
* **Java 21+** — built and tested with Temurin 21; no preview features required.

## Maven coordinates

```xml
<dependency>
  <groupId>com.coccoc</groupId>
  <artifactId>coccoc-tokenizer-java</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Notes

* The bundled dict jars (`multiterm.bin` ~19 MB, `syllable.bin` ~20 MB) add ~40 MB to the classpath. `bigram.bin` is optional and improves sticky-phrase segmentation when present.
* The int-constant API (`TOKENIZE_NORMAL`, `TOKENIZE_HOST`, `TOKENIZE_URL`) and the `segment4Transforming` / `segmentKeepPuncts` / `segmentUrl` overloads are provided for source-level compatibility with existing callers of the vendored `Tokenizer` class used in `elasticsearch-analysis-vietnamese`.

---

# Release 1.5

## Major Features and Improvement

* Before 1.5, punctuations and spaces are removed during normal tokenization, but are kept during tokenization for transformation, which is used internally by Coc Coc Search Engine. This update introduces option `keep_puncts` in `run_tokenize()` function, which can be used to keep punctuations (but not spaces and dots in segmented URLs) in normal tokenization.
* New argument `-k` and `-t` are introduced in CLI, to toggle `keep_puncts` and `for_transformation` when running tokenizer.

## Breaking changes

* Before 1.5, `run_tokenize()` has a param named `dont_push_puncts`, which is used to prevent inclusion of punctuations in result when tokenizing for transformation. It was replaced by `keep_puncts`, which serves the same purpose but (1) can be used for both normal tokenization and tokenization for transformation and (2) positive parameter naming is a better practice. The default value of `keep_puncts` is equal to `for_transformation` - false for normal tokenization, true for transformation. So previous behaviour remains the same, but this will break old code if `run_tokenize()` was called with `for_transformation = true` and `dont_push_puncts = false`.

## Other changes

* Wrapper functions are added to C++ implementation, matching those in Java binding, for ease of use.