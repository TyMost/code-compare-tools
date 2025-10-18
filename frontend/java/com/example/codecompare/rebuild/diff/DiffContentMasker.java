package com.example.codecompare.rebuild.diff;

/**
 * Masks source/target snapshots before diffing so that configured content can be ignored.
 */
@FunctionalInterface
public interface DiffContentMasker {

    /**
     * Scope of the content being processed.
     */
    enum Scope {
        SOURCE,
        TARGET
    }

    /**
     * Applies masking rules to the supplied content.
     *
     * @param content original code snapshot content (may be {@code null})
     * @param scope   whether the content belongs to the source or target side
     * @return content after masking (never {@code null})
     */
    String mask(String content, Scope scope);

    /**
     * @return a no-op masker that returns the original content unchanged.
     */
    static DiffContentMasker noop() {
        return (content, scope) -> content == null ? "" : content;
    }
}
