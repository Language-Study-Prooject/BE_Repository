package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWordsBatchRequest {

    @NotEmpty(message = "is required")
    @Valid
    private List<CreateWordRequest> words;
}
