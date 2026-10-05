package com.se183891.badminton_backend.auth.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void normalizesVietnamesePhoneFormats() {
        assertThat(PhoneNumbers.normalize("0901234567")).isEqualTo("0901234567");
        assertThat(PhoneNumbers.normalize("+84901234567")).isEqualTo("0901234567");
        assertThat(PhoneNumbers.normalize("090 123-45.67")).isEqualTo("0901234567");
        assertThat(PhoneNumbers.normalize("  ")).isNull();
        assertThat(PhoneNumbers.normalize(null)).isNull();
    }

    @Test
    void inputRegexAcceptsValidAndBlankOnly() {
        assertThat("0901234567").matches(PhoneNumbers.INPUT_REGEX);
        assertThat("+84 901 234 567").matches(PhoneNumbers.INPUT_REGEX);
        assertThat("").matches(PhoneNumbers.INPUT_REGEX);
        assertThat("12345").doesNotMatch(PhoneNumbers.INPUT_REGEX);
        assertThat("09012345678").doesNotMatch(PhoneNumbers.INPUT_REGEX);
    }
}
