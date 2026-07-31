package com.everyarrow.eventengine.spi.handler;

public class ServiceCallHandlerException extends RuntimeException {

	private static final long serialVersionUID = 1L;

    public ServiceCallHandlerException(String errorMsg) {
        super(errorMsg);
    }

	public ServiceCallHandlerException(String message, Throwable cause) {
		super(message, cause);
	}

	public ServiceCallHandlerException(Throwable cause) {
		super(cause);
	}
}
