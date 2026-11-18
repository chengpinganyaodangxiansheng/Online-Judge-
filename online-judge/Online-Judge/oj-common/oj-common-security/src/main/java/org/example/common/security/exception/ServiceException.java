package org.example.common.security.exception;

import lombok.Data;
import org.example.common.core.enums.ResultCode;

@Data
public class ServiceException extends RuntimeException {

    private ResultCode resultCode;

    public ServiceException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
    }
}
