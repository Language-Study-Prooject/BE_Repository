package com.mzc.secondproject.serverless.domain.grammar.factory;

import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;
import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarLevel;

public interface GrammarCheckFactory {
	GrammarCheckResponse checkGrammar(String sentence, GrammarLevel level);
}
