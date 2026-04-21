package com.xgjktech.reportrelation.strategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BizTypeHandlerRouter {

    private final Map<Long, BizTypeHandler> handlerByTypeId;

    public BizTypeHandlerRouter(List<BizTypeHandler> handlers) {
        this.handlerByTypeId = handlers.stream()
                .collect(Collectors.toMap(BizTypeHandler::typeId, h -> h));
        log.info("已注册业务类型处理器: {}",
                handlerByTypeId.entrySet().stream()
                        .map(e -> e.getKey() + " -> " + e.getValue().bizType())
                        .collect(Collectors.joining(", ")));
    }

    public BizTypeHandler getHandler(Long typeId) {
        return handlerByTypeId.get(typeId);
    }
}
