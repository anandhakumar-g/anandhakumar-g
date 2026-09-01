package com.singlepoint.crypto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * Transparently encrypts/decrypts a String column at the JPA boundary.
 * Apply with {@code @Convert(converter = EncryptedStringConverter.class)} on entity fields
 * such as phone / email / contact phone. See docs/decisions.md ADR-004.
 *
 * JPA instantiates converters itself, so the {@link CryptoService} is injected through a
 * static holder populated once the Spring context is up.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile CryptoService cryptoService;

    @Autowired
    void setCryptoService(CryptoService service) {
        EncryptedStringConverter.cryptoService = service;
    }

    private static CryptoService crypto() {
        CryptoService c = cryptoService;
        if (c == null) {
            throw new IllegalStateException("CryptoService not initialised yet");
        }
        return c;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return crypto().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return crypto().decrypt(dbData);
    }
}
