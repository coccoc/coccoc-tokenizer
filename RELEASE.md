# Release 1.5

## Major Features and Improvement

* Before 1.5, punctuations and spaces are removed during normal tokenization, but are kept during tokenization for transformation, which is used internally by Coc Coc Search Engine. This update introduces option `keep_puncts` in `run_tokenize()` function, which can be used to keep punctuations (but not spaces and dots in segmented URLs) in normal tokenization.
* New argument `-k` and `-t` are introduced in CLI, to toggle `keep_puncts` and `for_transformation` when running tokenizer.

## Breaking changes

* Before 1.5, `run_tokenize()` has a param named `dont_push_puncts`, which is used to prevent inclusion of punctuations in result when tokenizing for transformation. It was replaced by `keep_puncts`, which serves the same purpose but (1) can be used for both normal tokenization and tokenization for transformation and (2) positive parameter naming is a better practice. The default value of `keep_puncts` is equal to `for_transformation` - false for normal tokenization, true for transformation. So previous behaviour remains the same, but this will break old code if `run_tokenize()` was called with `for_transformation = true` and `dont_push_puncts = false`.

## Other changes

* Wrapper functions are added to C++ implementation, matching those in Java binding, for ease of use.