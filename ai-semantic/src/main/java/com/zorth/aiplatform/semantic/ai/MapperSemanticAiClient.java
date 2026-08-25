package com.zorth.aiplatform.semantic.ai;

import com.zorth.aiplatform.semantic.model.MapperSemantic;

public interface MapperSemanticAiClient {

    MapperSemantic extract(String systemPrompt, String userPrompt);
}
