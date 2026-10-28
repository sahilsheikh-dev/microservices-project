package com.user_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserUpdateRequestTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_nameOnlyUpdate_allowsMissingFields() {
        assertThat(validator.validate(new UserUpdateRequest("x", null, null, null))).isEmpty();
    }

    @Test
    void validate_presentFieldsOutsideConstraints_reportsEachInvalidField() {
        assertThat(validator.validate(new UserUpdateRequest("x".repeat(101), "not-an-email",
                "short", "SUPERUSER")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "name", "email", "password", "roles");
    }

    @Test
    void validate_fieldsAtMaximumLengths_acceptsRequest() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63)
                + "." + "d".repeat(61);

        assertThat(validator.validate(new UserUpdateRequest("x".repeat(100), email,
                "p".repeat(72), "ADMIN"))).isEmpty();
    }

    @Test
    void validate_emailAndPasswordOverMaximum_reportsThoseFields() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63)
                + "." + "d".repeat(62);

        assertThat(validator.validate(new UserUpdateRequest(null, email, "p".repeat(73), null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("email", "password");
    }

    @Test
    void validate_blankName_whenPresent_reportsNameViolation() {
        assertThat(validator.validate(new UserUpdateRequest("   ", null, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("name");
    }
}
