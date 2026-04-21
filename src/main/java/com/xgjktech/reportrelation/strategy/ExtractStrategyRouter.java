package com.xgjktech.reportrelation.strategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExtractStrategyRouter {

    private final Map<String, ExtractStrategy> strategyMap;

    public ExtractStrategyRouter(List<ExtractStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ExtractStrategy::bizType, Function.identity()));
        log.info("已注册提取策略: {}", strategyMap.keySet());
    }

    public ExtractStrategy getStrategy(String bizType) {
        ExtractStrategy strategy = strategyMap.get(bizType);
        if (strategy == null) {
            log.warn("未找到bizType={}的提取策略", bizType);
        }
        return strategy;
    }

    public boolean hasStrategy(String bizType) {
        return strategyMap.containsKey(bizType);
    }
}
