package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import java.util.List;

public class BatchGetWordsRequest {
    private List<String> wordIds;

    public List<String> getWordIds() {
        return wordIds;
    }

    public void setWordIds(List<String> wordIds) {
        this.wordIds = wordIds;
    }
}
