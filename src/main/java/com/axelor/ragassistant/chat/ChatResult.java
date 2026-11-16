package com.axelor.ragassistant.chat;

import java.util.List;

public record ChatResult(String answer, List<SourceReference> sources) {

    public ChatResult {
        sources = List.copyOf(sources);
    }
}
