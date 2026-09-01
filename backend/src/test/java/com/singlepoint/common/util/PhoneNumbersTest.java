package com.singlepoint.common.util;

import com.singlepoint.common.error.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumbersTest {

    @Test
    void normalizesIndianTenDigit() {
        assertEquals("+919876543210", PhoneNumbers.normalize("9876543210"));
        assertEquals("+919876543210", PhoneNumbers.normalize("09876543210"));
        assertEquals("+919876543210", PhoneNumbers.normalize("98765 43210"));
        assertEquals("+919876543210", PhoneNumbers.normalize("+91 98765-43210"));
    }

    @Test
    void keepsOtherCountryCodes() {
        assertEquals("+14155550123", PhoneNumbers.normalize("+1 415 555 0123"));
        assertEquals("+441632960961", PhoneNumbers.normalize("0044 1632 960961"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(AppException.class, () -> PhoneNumbers.normalize("12345"));
        assertThrows(AppException.class, () -> PhoneNumbers.normalize("not-a-number"));
        assertThrows(AppException.class, () -> PhoneNumbers.normalize(null));
    }

    @Test
    void maskShowsLastFour() {
        assertTrue(PhoneNumbers.mask("+919876543210").endsWith("3210"));
        assertFalse(PhoneNumbers.mask("+919876543210").contains("9876"));
    }
}
