package com.zorth.aiplatform.semantic.generation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import com.zorth.aiplatform.semantic.exception.SemanticGenerationAlreadyRunningException;

public final class MapperSemanticBatchGuard {

    private final AtomicBoolean running = new AtomicBoolean(false);

    public <T> T run(Supplier<T> action) {
        if (!running.compareAndSet(false, true)) {
            throw new SemanticGenerationAlreadyRunningException();
        }
        try {
            return action.get();
        }
        finally {
            running.set(false);
        }
    }
}
