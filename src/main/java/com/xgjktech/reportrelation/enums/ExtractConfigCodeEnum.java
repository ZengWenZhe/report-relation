package com.xgjktech.reportrelation.enums;

import lombok.Getter;

@Getter
public enum ExtractConfigCodeEnum {

    REPORT_EXTRACT_SCHEMA("REPORT_EXTRACT_SCHEMA", "汇报结构化提取");

    private final String code;

    private final String name;

    ExtractConfigCodeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ExtractConfigCodeEnum getByCode(String code) {
        for (ExtractConfigCodeEnum configCode : values()) {
            if (configCode.getCode().equals(code)) {
                return configCode;
            }
        }
        return null;
    }
}
