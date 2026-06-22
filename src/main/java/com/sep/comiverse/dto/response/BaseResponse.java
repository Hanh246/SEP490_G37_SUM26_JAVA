package com.sep.comiverse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    @JsonProperty("success")
    @Builder.Default
    protected boolean success = true;
    @JsonProperty("data")
    protected T data;
    @JsonProperty("message")
    protected String message;
    @JsonProperty("errors")
    protected Map<String, String> errors;

    public BaseResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(boolean success, T data) {
        this.success = success;
        this.data = data;
    }
}