package dev.chasem.smslauncher;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.Adler32;

/**
 * Applies a VCDIFF delta (RFC 3284), as written by xdelta3: the game's tools/mods/vcdiff.py in
 * Java. Supports the default code table, VCD_SOURCE and VCD_TARGET windows and xdelta3's
 * per-window Adler-32 checksum; deltas with secondary compression or a custom code table are
 * refused.
 */
final class Vcdiff {
    interface Progress { void update(long deltaBytesRead) throws IOException; }

    static final class VcdiffException extends IOException {
        private static final long serialVersionUID = 1L;
        VcdiffException(String message) { super(message); }
    }

    private static final int NOOP = 0, ADD = 1, RUN = 2, COPY = 3, S_NEAR = 4, S_SAME = 3;
    // RFC 3284 section 5.6: each code is two instructions, {type, size, mode} each.
    private static final byte[][] CODE_TABLE = codeTable();

    private Vcdiff() {}

    private static byte[][] codeTable() {
        byte[][] t = new byte[256][];
        int i = 0;
        t[i++] = code(RUN, 0, 0, NOOP, 0, 0);
        for (int s = 0; s < 18; s++) t[i++] = code(ADD, s, 0, NOOP, 0, 0);
        for (int mode = 0; mode < 9; mode++) {
            t[i++] = code(COPY, 0, mode, NOOP, 0, 0);
            for (int s = 4; s < 19; s++) t[i++] = code(COPY, s, mode, NOOP, 0, 0);
        }
        for (int mode = 0; mode < 6; mode++)
            for (int a = 1; a < 5; a++) for (int c = 4; c < 7; c++) t[i++] = code(ADD, a, 0, COPY, c, mode);
        for (int mode = 6; mode < 9; mode++) for (int a = 1; a < 5; a++) t[i++] = code(ADD, a, 0, COPY, 4, mode);
        for (int mode = 0; mode < 9; mode++) t[i++] = code(COPY, 4, mode, ADD, 1, 0);
        if (i != 256) throw new AssertionError(i);
        return t;
    }
    private static byte[] code(int t1, int s1, int m1, int t2, int s2, int m2) {
        return new byte[]{(byte) t1, (byte) s1, (byte) m1, (byte) t2, (byte) s2, (byte) m2};
    }

    /** A window's section, read with a position. */
    private static final class Section {
        byte[] buf = new byte[0];
        int len, pos;
        void read(DataInputStream in, int n) throws IOException {
            if (buf.length < n) buf = new byte[n];
            in.readFully(buf, 0, n);
            len = n; pos = 0;
        }
        int next() throws VcdiffException {
            if (pos >= len) throw new VcdiffException("a window section ends early");
            return buf[pos++] & 0xff;
        }
        long varint() throws VcdiffException {
            long v = 0;
            for (int i = 0; i < 9; i++) {
                int b = next();
                v = (v << 7) | (b & 0x7f);
                if ((b & 0x80) == 0) return v;
            }
            throw new VcdiffException("a number in the delta is too long");
        }
    }

    private static final class CountingInput extends FilterInputStream {
        long count;
        CountingInput(InputStream in) { super(in); }
        @Override public int read() throws IOException { int b = super.read(); if (b >= 0) count++; return b; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len); if (n > 0) count += n; return n;
        }
        @Override public long skip(long n) throws IOException { long k = super.skip(n); count += k; return k; }
    }

    private static long varint(DataInputStream in) throws IOException {
        long v = 0;
        for (int i = 0; i < 9; i++) {
            int b = in.read();
            if (b < 0) throw new VcdiffException("the delta is truncated");
            v = (v << 7) | (b & 0x7f);
            if ((b & 0x80) == 0) return v;
        }
        throw new VcdiffException("a number in the delta is too long");
    }
    private static int size(long v, String what) throws VcdiffException {
        if (v < 0 || v > (256 << 20)) throw new VcdiffException(what + " is too large");
        return (int) v;
    }

    /** Writes out = delta applied to source. The delta is read once, front to back. */
    static void apply(File source, InputStream deltaStream, File out, Progress progress) throws IOException {
        try (RandomAccessFile sf = new RandomAccessFile(source, "r"); RandomAccessFile of = new RandomAccessFile(out, "rw")) {
            of.setLength(0);
            FileChannel sc = sf.getChannel();
            long sourceLength = sc.size();
            // The source is mapped, not read: a window's source segment can be tens of MB.
            ByteBuffer src = sourceLength > 0 ? sc.map(FileChannel.MapMode.READ_ONLY, 0, sourceLength) : ByteBuffer.allocate(0);
            CountingInput counted = new CountingInput(new BufferedInputStream(deltaStream, 1 << 20));
            DataInputStream df = new DataInputStream(counted);
            byte[] magic = new byte[4];
            df.readFully(magic);
            if ((magic[0] & 0xff) != 0xd6 || (magic[1] & 0xff) != 0xc3 || (magic[2] & 0xff) != 0xc4 || magic[3] != 0)
                throw new VcdiffException("not a VCDIFF delta");
            int hdr = df.readUnsignedByte();
            if ((hdr & 1) != 0) throw new VcdiffException("secondary compression is not supported");
            if ((hdr & 2) != 0) throw new VcdiffException("custom code tables are not supported");
            if ((hdr & 4) != 0) df.skipBytes(size(varint(df), "the application header"));
            Section data = new Section(), inst = new Section(), addr = new Section();
            byte[] target = new byte[0], targetSeg = new byte[0];
            long written = 0;
            Adler32 adler = new Adler32();
            for (int win; (win = df.read()) >= 0; ) {
                int slen = 0;
                ByteBuffer seg = null;
                if ((win & 3) != 0) {
                    slen = size(varint(df), "a source segment");
                    long spos = varint(df);
                    if ((win & 1) != 0) {
                        if (spos + slen > sourceLength) throw new VcdiffException("a source segment is past the end of the source");
                        seg = src.duplicate();
                        seg.position((int) spos);
                    } else {
                        if (spos + slen > written) throw new VcdiffException("a target segment is past the data decoded so far");
                        if (targetSeg.length < slen) targetSeg = new byte[slen];
                        of.seek(spos);
                        of.readFully(targetSeg, 0, slen);
                        of.seek(written);
                        seg = ByteBuffer.wrap(targetSeg, 0, slen);
                    }
                }
                varint(df);  // length of the delta encoding
                int tlen = size(varint(df), "a target window");
                if (df.readUnsignedByte() != 0) throw new VcdiffException("compressed window sections are not supported");
                int dlen = size(varint(df), "a data section"), ilen = size(varint(df), "an instruction section"),
                    alen = size(varint(df), "an address section");
                long cksum = -1;
                if ((win & 4) != 0) cksum = df.readInt() & 0xffffffffL;
                data.read(df, dlen); inst.read(df, ilen); addr.read(df, alen);
                if (target.length < tlen) target = new byte[tlen];
                decodeWindow(target, tlen, seg, slen, data, inst, addr);
                if (cksum >= 0) {
                    adler.reset(); adler.update(target, 0, tlen);
                    if (adler.getValue() != cksum)
                        throw new VcdiffException("checksum mismatch in the window at output offset " + written);
                }
                of.write(target, 0, tlen);
                written += tlen;
                if (progress != null) progress.update(counted.count);
            }
        }
    }

    private static void decodeWindow(byte[] target, int tlen, ByteBuffer seg, int slen,
                                     Section data, Section inst, Section addr) throws VcdiffException {
        long[] near = new long[S_NEAR];
        long[] same = new long[S_SAME * 256];
        int nextSlot = 0, tpos = 0;
        int segStart = seg == null ? 0 : seg.position();
        while (inst.pos < inst.len) {
            byte[] c = CODE_TABLE[inst.next()];
            for (int half = 0; half < 2; half++) {
                int type = c[half * 3], mode = c[half * 3 + 2];
                if (type == NOOP) continue;
                long size = c[half * 3 + 1];
                if (size == 0) size = inst.varint();
                if (size > tlen - tpos) throw new VcdiffException("an instruction runs past its window");
                int n = (int) size;
                if (type == ADD) {
                    if (n > data.len - data.pos) throw new VcdiffException("a data section ends early");
                    System.arraycopy(data.buf, data.pos, target, tpos, n);
                    data.pos += n; tpos += n;
                } else if (type == RUN) {
                    byte b = (byte) data.next();
                    java.util.Arrays.fill(target, tpos, tpos + n, b);
                    tpos += n;
                } else {
                    long here = (long) slen + tpos, a;
                    if (mode == 0) a = addr.varint();
                    else if (mode == 1) a = here - addr.varint();
                    else if (mode < 2 + S_NEAR) a = near[mode - 2] + addr.varint();
                    else a = same[(mode - 2 - S_NEAR) * 256 + addr.next()];
                    near[nextSlot] = a;
                    nextSlot = (nextSlot + 1) % S_NEAR;
                    same[(int) (a % (S_SAME * 256))] = a;
                    if (a < 0 || a >= here) throw new VcdiffException("a copy address is past the data decoded so far");
                    if (a < slen) {  // from the source segment, maybe running into the target
                        int k = (int) Math.min(n, slen - a);
                        ByteBuffer from = seg.duplicate();
                        from.position(segStart + (int) a);
                        from.get(target, tpos, k);
                        tpos += k; n -= k; a = slen;
                    }
                    int ta = (int) (a - slen);
                    while (n > 0) {  // overlapping copies repeat the pattern [ta, tpos)
                        int k = Math.min(n, tpos - ta);
                        System.arraycopy(target, ta, target, tpos, k);
                        tpos += k; n -= k;
                    }
                }
            }
        }
        if (tpos != tlen || data.pos != data.len || addr.pos != addr.len)
            throw new VcdiffException("window sections do not add up");
    }
}
