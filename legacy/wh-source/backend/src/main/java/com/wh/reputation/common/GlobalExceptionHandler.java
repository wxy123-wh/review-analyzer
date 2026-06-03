package com.wh.reputation.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("请求参数错误 请求编号={} {} {} 参数={}：{}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                paramsOf(request),
                ex.getMessage()
        );
        return ApiResponse.error(400, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在 请求编号={} {} {} 参数={}：{}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                paramsOf(request),
                ex.getMessage()
        );
        return ApiResponse.error(404, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("未授权 请求编号={} {} {} 参数={}：{}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                paramsOf(request),
                ex.getMessage()
        );
        return ApiResponse.error(401, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("请求体解析失败 请求编号={} {} {}：{}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                ex.getMessage()
        );
        return ApiResponse.error(400, "请求体格式不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("缺少参数 请求编号={} {} {}：{}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                ex.getMessage()
        );
        return ApiResponse.error(400, "缺少参数：" + paramLabel(ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("参数类型错误 请求编号={} {} {}：{}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                ex.getMessage()
        );
        return ApiResponse.error(400, "参数类型不正确：" + paramLabel(ex.getName()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("静态资源不存在 请求编号={} {} {}", requestIdOf(request), methodOf(request), uriOf(request), ex);
        return ApiResponse.error(404, "资源不存在");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("接口不存在 请求编号={} {} {}", requestIdOf(request), methodOf(request), uriOf(request), ex);
        return ApiResponse.error(404, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("服务异常 请求编号={} {} {} 参数={}",
                requestIdOf(request),
                methodOf(request),
                uriOf(request),
                paramsOf(request),
                ex
        );
        return ApiResponse.error(500, "服务异常");
    }

    private static String requestIdOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? null : String.valueOf(value);
    }

    private static String methodOf(HttpServletRequest request) {
        return request == null ? null : request.getMethod();
    }

    private static String uriOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String uri = request.getRequestURI();
        String qs = request.getQueryString();
        if (qs == null || qs.isBlank()) {
            return uri;
        }
        return uri + "?" + qs;
    }

    private static String paramsOf(HttpServletRequest request) {
        if (request == null) {
            return "{}";
        }
        Map<String, String[]> map = request.getParameterMap();
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + Arrays.toString(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String paramLabel(String name) {
        if (name == null || name.isBlank()) {
            return "未知参数";
        }
        return switch (name) {
            case "productId" -> "产品编号";
            case "platformId" -> "平台编号";
            case "aspectId" -> "维度编号";
            case "clusterId" -> "聚类编号";
            case "eventId" -> "事件编号";
            case "start" -> "开始日期";
            case "end" -> "结束日期";
            case "page" -> "页码";
            case "pageSize" -> "每页数量";
            case "keyword" -> "关键词";
            case "sentiment" -> "情感";
            case "granularity" -> "时间粒度";
            case "topN" -> "数量";
            case "status" -> "状态";
            case "id" -> "编号";
            default -> "未知参数";
        };
    }
}
