package com.excelman.fileossuploadandwebsocketconnection.result;

import java.util.HashMap;
import java.util.Map;

public class CodeMsg {

    private int code;
    private String msg;
    /**
     * 0： 成功, HttpStatus.OK(200)
     * 100000-200000: 基础类错误码
     *      100000-101000 系统类
     *          100000-100100 InternalError类, HttpStatus.INTERNAL_SERVER_ERROR(500)
     *          100100-100200 RequestError类, HttpStatus.BAD_REQUEST(400)
     *          ...
     *      101000-102000 鉴权类, HttpStatus.BAD_REQUEST(400)
     *          101000-101100 AuthError类
     *          101100-101200 RegistError类
     *          ...
     * 200000-201000 参数校验类, HttpStatus.BAD_REQUEST(400)
     *          200000-200100 InvalidParam类
     *          200100-200200 InvalidFile类
     *          ...
     *
     * 900000-901000 其他错误类, HttpStatus.BAD_REQUEST(400)
     *
     */
    private static final Map<String, CodeMsg> sMaps = new HashMap<>();

    //**0:成功, HttpStatus.OK(200)**//
    public static final CodeMsg Success = new CodeMsg(0, "成功");

    //***900000-901000 其他错误类, HttpStatus.BAD_REQUEST(400)**//
    public static final CodeMsg OtherError = new CodeMsg(900000, "其他错误[%s]");

    private CodeMsg() {
    }

    private CodeMsg(int code, String msg) {
        this.code = code;
        this.msg = msg;
        sMaps.put("" + code, this);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public CodeMsg fillArgs(Object... args) {
        CodeMsg msg = new CodeMsg();
        msg.code = this.code;
        msg.msg = String.format(this.msg, args);
        return msg;
    }

    public static CodeMsg fromResultCode(int ret){
        return sMaps.get("" + ret);
    }

    @Override
    public String toString() {
        return "CodeMsg [code=" + code + ", msg=" + msg + "]";
    }

}
