package org.example.common.core.domain;

import lombok.Data;
import org.example.common.core.enums.ResultCode;

@Data
public class Result <T> {
    /**
     * 消息状态码
     */
    private int code;
    /**
     * 消息内容
     */
    private String msg;
    /**
     * 数据对象
     */
    private T data;

    public static <T> Result<T> success(T data) {
        return assembleResult(data, ResultCode.SUCCESS);
    }

    public static <T> Result<T> success() {
        return assembleResult(null, ResultCode.SUCCESS);
    }

    /**
     * 指定错误
     * @param resultCode
     * @return
     * @param <T>
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return assembleResult(null, resultCode);
    }


    public static <T> Result<T> fail() {
        return assembleResult(null, ResultCode.FAILED);
    }


    public static <T> Result<T> fail(int code, String msg) {
        return assembleResult(code,msg,null);
    }

    public static <T> Result<T> assembleResult(T data, ResultCode code) {
        Result<T>  result = new Result<>();
        result.setCode(code.getCode());
        result.setMsg(code.getMsg());
        result.setData(data);
        return result;
    }

    //<T> 放在返回值之前，声明这是一个泛型方法，引入了一个类型参数 T，它可以在整个方法签名中使用。 Result<T> 表示该方法返回一个 Result 类型的对象，并且这个 Result 对象内部包含的数据类型就是 T
    public static <T> Result<T> assembleResult(int code, String msg,T data) {
        Result<T>  result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }
}
