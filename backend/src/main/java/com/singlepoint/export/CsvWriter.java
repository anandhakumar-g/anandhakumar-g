package com.singlepoint.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * MVP-12 (A): a minimal RFC-4180 CSV writer — no library. UTF-8 with a BOM (so Excel reads
 * accents), CRLF row endings, a field quoted only when it contains {@code , " CR LF}, inner
 * quotes doubled. Streams straight to the response {@link OutputStream}.
 */
public final class CsvWriter implements AutoCloseable {

    private final Writer out;

    public CsvWriter(OutputStream os) throws IOException {
        this.out = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        out.write('﻿'); // BOM
    }

    public void row(Object... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.write(',');
            out.write(escape(fields[i]));
        }
        out.write("\r\n");
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    private static String escape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
