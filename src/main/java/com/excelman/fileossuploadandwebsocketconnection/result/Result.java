package com.excelman.fileossuploadandwebsocketconnection.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class Result<T> {
    public static final String KEY_TOTAL = "total";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_DATA = "data";

    public static final int SUCCESS_CODE = CodeMsg.Success.getCode();
    private static final String DEFAULT_MSG = "成功";
    private int code;
    private String msg;
    private T data;


    /**
     * 成功时候的调用
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>(data);
    }

    /**
     * 成功且需要自定义msg的时候调用
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<T>(msg, data);
    }

    /**
     * 失败时候的调用
     */
    public static <T> Result<T> error(CodeMsg codeMsg) {
        return new Result<T>(codeMsg);
    }

    /**
     * 新增结束--------------------------------------
     */

    private Result(T data) {
        this.code = SUCCESS_CODE;
        this.msg = DEFAULT_MSG;
        this.data = data;
    }

    private Result(String msg, T data) {
        this.msg = msg;
        this.data = data;
    }


    private Result(CodeMsg codeMsg) {
        if (codeMsg != null) {
            this.code = codeMsg.getCode();
            this.msg = codeMsg.getMsg();
        }
    }
}