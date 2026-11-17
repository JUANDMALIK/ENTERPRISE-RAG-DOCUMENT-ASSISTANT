package com.axelor.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.retrieval")
public record RagRetrievalProperties(int topK, double similarityThreshold) {

    public static final int MAX_TOP_K = 100;

    public RagRetrievalProperties {
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("RAG top-k must be between 1 and " + MAX_TOP_K + ".");
        }
        if (!Double.isFinite(similarityThreshold)
                || similarityThreshold < 0.0
                || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("RAG similarity threshold must be between 0.0 and 1.0.");
        }
    }
}
