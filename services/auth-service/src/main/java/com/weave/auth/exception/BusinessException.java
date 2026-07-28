package com.weave.auth.exception;

import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.exception.AbstractBusinessException;

public class BusinessException extends AbstractBusinessException {

    public BusinessException(AuthApiStatus status) {
        super(status);
    }
}

