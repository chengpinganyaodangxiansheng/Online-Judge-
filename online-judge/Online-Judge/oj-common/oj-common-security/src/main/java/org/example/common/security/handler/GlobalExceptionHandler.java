package org.example.common.security.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.common.core.domain.Result;
import org.example.common.core.enums.ResultCode;
import org.example.common.security.exception.ServiceException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

//当抛出异常时， @RestControllerAdvice 标注的类将被⾃动调⽤，
//并根据异常类型和处理程序的注解来决定如何处理该异常。这使得开发者可以在整个应⽤程序范围内
//统⼀处理异常。
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 请求⽅式不⽀持
     */
    //@ExceptionHandler⼀般与@RestControllerAdvice配合使⽤，使⽤其来捕
    //获和处理不同类型的异常。
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不⽀持'{}'请求", requestURI, e.getMethod());
        return Result.fail(ResultCode.ERROR);
    }

    /**
     * 拦截运⾏时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result  handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发⽣异常.", requestURI, e);
        return Result.fail(ResultCode.ERROR);
    }


    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public Result  handleException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发⽣异常.", requestURI, e);
        return Result.fail(ResultCode.ERROR);
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(ServiceException.class)
    public Result  handleServiceException(ServiceException e, HttpServletRequest request) {
        ResultCode resultCode = e.getResultCode();
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发⽣业务异常.", requestURI, resultCode.getMsg(),e);
        return Result.fail(resultCode);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        log.error(e.getMessage());
        //e.getAllErrors()：获取所有绑定错误对象   DefaultMessageSourceResolvable::getDefaultMessage：方法引用，从错误对象中提取预设的验证失败消息（如"不能为空"、"长度不符合"等）
        String message = join(e.getAllErrors(), DefaultMessageSourceResolvable::getDefaultMessage, ", ");
        return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE.getCode(), message);
    }

    //遍历错误对象集合
    //对每个错误对象调用getDefaultMessage()获取错误文本
    //用逗号+空格连接所有非空错误消息
    private <E> String join(Collection<E> collection, Function<E, String> function, CharSequence delimiter) {
        if (CollUtil.isEmpty(collection)) {
            return StrUtil.EMPTY;
        }
        return collection.stream().map(function).filter(Objects::nonNull).collect(Collectors.joining(delimiter));
    }
}
