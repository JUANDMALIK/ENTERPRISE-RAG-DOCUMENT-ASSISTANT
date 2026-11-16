package com.axelor.ragassistant.document;

public class DocumentProcessingCapacityException extends RuntimeException {

    public DocumentProcessingCapacityException() {
        super("Document processing is busy. Please try again shortly.");
    }
}
