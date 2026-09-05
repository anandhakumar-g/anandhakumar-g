package com.singlepoint.export.api;

import com.singlepoint.export.CsvWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;

/**
 * MVP-12 (A): renders a CSV-writing lambda into an attachment response.
 * <p>
 * The body is built on the calling (request) thread and buffered — not streamed — because the
 * export queries hit RLS-forced tables and depend on the {@code TenantContext} / {@code UserContext}
 * ThreadLocals that {@code JwtAuthFilter} sets for the request; a {@code StreamingResponseBody}
 * runs on a separate async thread where that context is absent and every query fails closed.
 * At demo scale the datasets are small; {@code ?from=&to=} is the size pressure valve.
 */
final class Csv {

    private Csv() { }

    interface Body {
        void write(CsvWriter w) throws IOException;
    }

    static ResponseEntity<byte[]> download(String name, Body body) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (CsvWriter w = new CsvWriter(buffer)) {
            body.write(w);
        } catch (IOException e) {
            throw new UncheckedIOException("CSV export failed: " + name, e);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "-" + LocalDate.now() + ".csv\"")
                .body(buffer.toByteArray());
    }
}
