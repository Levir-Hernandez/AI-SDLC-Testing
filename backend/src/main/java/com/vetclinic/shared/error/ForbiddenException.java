package com.vetclinic.shared.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
