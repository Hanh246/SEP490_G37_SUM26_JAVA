package com.sep.comiverse.unit.dto;

import com.sep.comiverse.dto.request.RegisterRequest;
import com.sep.comiverse.dto.request.ResetPasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void registrationUsesTheSharedClientBoundaries() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("Reader.One_2");
        request.setFullName("Nguyễn-Anh O'Neil");
        request.setEmail("reader@example.com");
        request.setPassword("12345678");

        assertThat(validator.validate(request)).isEmpty();

        request.setPassword("1234567");
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("password");
    }

    @Test
    void resetRequiresSixDigitOtpAndAnEightCharacterPassword() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("reader@example.com");
        request.setOtp("12345a");
        request.setNewPassword("1234567");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("otp", "newPassword");

        request.setOtp("123456");
        request.setNewPassword("12345678");
        assertThat(validator.validate(request)).isEmpty();
    }
}
