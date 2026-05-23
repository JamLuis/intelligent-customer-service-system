package com.company.smartsupport.graph;

import com.company.smartsupport.common.SmartSupportException;

public class EmbeddingVersionMismatchException extends SmartSupportException {

    public EmbeddingVersionMismatchException(String message) {
        super("ICSS-KG-422-EMBEDDING_VERSION_MISMATCH", message);
    }
}