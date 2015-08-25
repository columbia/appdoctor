package com.andchecker;

public class CheckerException extends ACIException {
	private static final long serialVersionUID = 1L;
	CheckerException(String checker, String msg) {
		super(ACIException.LEVEL_ERROR, checker, msg);
	}
}

class CheckerWarning extends ACIException {
	private static final long serialVersionUID = 1L;
	CheckerWarning(String checker, String msg) {
		super(ACIException.LEVEL_WARNING, checker, msg);
	}
}
