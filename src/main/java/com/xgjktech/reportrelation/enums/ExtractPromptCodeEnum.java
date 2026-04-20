package com.xgjktech.reportrelation.enums;

import lombok.Getter;

/**
 * 提取提示词编码枚举
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Getter
public enum ExtractPromptCodeEnum {

    REPORT_EXTRACT_SCHEMA("REPORT_EXTRACT_SCHEMA", "汇报结构化提取提示词");

    private final String code;

    private final String name;

    ExtractPromptCodeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ExtractPromptCodeEnum getByCode(String code) {
        for (ExtractPromptCodeEnum promptCode : values()) {
            if (promptCode.getCode().equals(code)) {
                return promptCode;
            }
        }
        return null;
    }
}
