package com.coccoc.internal.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Port of BufferedReader::next_int() from tokenizer/auxiliary/buffered_reader.hpp:44-67.
 *
 * Encoding (from C++ comment): little-endian, where the FIRST byte of each integer
 * has bit 7 = 0 (0xxxxxxx), and CONTINUATION bytes have bit 7 = 1 (1xxxxxxx).
 * A byte with bit 7 = 0 encountered when power > 0 terminates the current integer
 * and starts the next one (it is saved and consumed first on the next call).
 *
 * This is the INVERSE of standard LEB128: in LEB128 bit-7=1 means "continue",
 * here bit-7=1 means "this is a continuation byte" while bit-7=0 means "new integer".
 */
public final class VarintReader implements AutoCloseable {

    private final InputStream in;
    private int savedByte = -1; // -1 = no saved byte (mirrors C++ last_byte_read = 0xFF)

    public VarintReader(InputStream in) {
        this.in = in;
    }

    /**
     * Read the next integer from the varint stream.
     *
     * @return decoded integer, or -1 if EOF (no more data)
     * @throws IOException on stream read errors
     */
    public int nextInt() throws IOException {
        int res;
        int power;

        if (savedByte >= 0) {
            // Restore the byte that was peeked by the previous call
            res   = savedByte & 0x7F;
            power = 7;
            savedByte = -1;
        } else {
            // Read the first byte of this integer (high bit must be 0)
            int d = in.read();
            if (d == -1) return -1;
            res   = d & 0x7F;
            power = 7;
        }

        // Read continuation bytes (high bit = 1) until we see a new-start byte (high bit = 0)
        while (true) {
            int d = in.read();
            if (d == -1) break;
            if ((d & 0x80) == 0) {
                // This byte starts the next integer — save it for the next call
                savedByte = d;
                break;
            }
            res |= (d & 0x7F) << power;
            power += 7;
        }
        return res;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
