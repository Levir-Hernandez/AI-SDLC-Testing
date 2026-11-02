package com.vetclinic.shared.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends DomainException {
    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
