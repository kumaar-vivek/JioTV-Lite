package com.example.jiotvservice.api;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

public final class RawResponseReader {
    private static final int BUFFER_SIZE = 8192;

    private RawResponseReader() {}

    public static String read(ResponseBody body) throws IOException {
        return readChunked(body).getBody();
    }

    public static Result readChunked(ResponseBody body) throws IOException {
        if (body == null) {
            return new Result("", 0);
        }

        Charset charset = charset(body);
        Reader reader = null;

        try {
            reader = new InputStreamReader(openResponseStream(body), charset);
            StringBuilder builder = new StringBuilder(initialCapacity(body.contentLength()));
            char[] buffer = new char[BUFFER_SIZE];
            int chunks = 0;
            int read;

            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
                chunks++;
            }

            return new Result(builder.toString(), chunks);
        } finally {
            if (reader != null) {
                reader.close();
            } else {
                body.close();
            }
        }
    }

    public static String readQuietly(ResponseBody body) {
        try {
            return read(body);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static InputStream openResponseStream(ResponseBody body) throws IOException {
        BufferedInputStream input = new BufferedInputStream(body.byteStream());
        input.mark(2);
        int first = input.read();
        int second = input.read();
        input.reset();

        if (first == 0x1F && second == 0x8B) {
            return new GZIPInputStream(input);
        }

        return input;
    }

    private static Charset charset(ResponseBody body) {
        MediaType contentType = body.contentType();
        return contentType == null ? StandardCharsets.UTF_8 :
                contentType.charset(StandardCharsets.UTF_8);
    }

    private static int initialCapacity(long contentLength) {
        if (contentLength <= 0 || contentLength > Integer.MAX_VALUE) {
            return BUFFER_SIZE;
        }
        return Math.max(BUFFER_SIZE, (int) contentLength);
    }

    public static final class Result {
        private final String body;
        private final int chunks;

        private Result(String body, int chunks) {
            this.body = body;
            this.chunks = chunks;
        }

        public String getBody() {
            return body;
        }

        public int getChunks() {
            return chunks;
        }
    }
}
