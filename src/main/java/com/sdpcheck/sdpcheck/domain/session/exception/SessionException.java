package com.sdpcheck.sdpcheck.domain.session.exception;

import com.sdpcheck.sdpcheck.global.exception.BusinessException;

public class SessionException extends BusinessException {

	public SessionException(SessionErrorCode errorCode) {
		super(errorCode);
	}
}
