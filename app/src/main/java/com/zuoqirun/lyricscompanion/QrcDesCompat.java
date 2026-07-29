package com.zuoqirun.lyricscompanion;

/**
 * QQ QRC's historical DES implementation. This intentionally preserves the
 * legacy S-box tables and 32-bit byte ordering used by QQMusicDecoder.
 * Adapted from Proify/LyricProvider (Apache-2.0).
 */
final class QrcDesCompat {
    private static final int DECRYPT = 0;
    private static final int[][] SBOX = {
            {14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13},
            {15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9},
            {10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12},
            {7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14},
            {2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3},
            {12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13},
            {4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12},
            {13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11}
    };
    private static final int[] KEY_SHIFTS = {1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1};
    private static final int[] KEY_C = {56,48,40,32,24,16,8,0,57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35};
    private static final int[] KEY_D = {62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,60,52,44,36,28,20,12,4,27,19,11,3};
    private static final int[] KEY_COMPRESSION = {13,16,10,23,0,4,2,27,14,5,20,9,22,18,11,3,25,7,15,6,26,19,12,1,40,51,30,36,46,54,29,39,50,44,32,47,43,48,38,55,33,52,45,41,49,35,28,31};
    private static final int[] IP_LEFT = {57,49,41,33,25,17,9,1,59,51,43,35,27,19,11,3,61,53,45,37,29,21,13,5,63,55,47,39,31,23,15,7};
    private static final int[] IP_RIGHT = {56,48,40,32,24,16,8,0,58,50,42,34,26,18,10,2,60,52,44,36,28,20,12,4,62,54,46,38,30,22,14,6};

    private QrcDesCompat() {}

    static byte[] decrypt(byte[] encrypted, byte[] key) {
        byte[][][] schedule = new byte[3][16][6];
        keySchedule(key, 16, schedule[0], DECRYPT);
        keySchedule(key, 8, schedule[1], 1);
        keySchedule(key, 0, schedule[2], DECRYPT);
        byte[] output = new byte[encrypted.length];
        byte[] inputBlock = new byte[8];
        byte[] resultBlock = new byte[8];
        for (int offset = 0; offset < encrypted.length; offset += 8) {
            int count = Math.min(8, encrypted.length - offset);
            java.util.Arrays.fill(inputBlock, (byte) 0);
            System.arraycopy(encrypted, offset, inputBlock, 0, count);
            crypt(inputBlock, resultBlock, schedule[0]);
            crypt(resultBlock, resultBlock, schedule[1]);
            crypt(resultBlock, resultBlock, schedule[2]);
            System.arraycopy(resultBlock, 0, output, offset, count);
        }
        return output;
    }

    private static void keySchedule(byte[] key, int offset, byte[][] schedule, int mode) {
        int c = 0;
        int d = 0;
        for (int i = 0; i < 28; i++) {
            c |= bit(key, KEY_C[i] + offset * 8, 31 - i);
            d |= bit(key, KEY_D[i] + offset * 8, 31 - i);
        }
        for (int i = 0; i < 16; i++) {
            c = ((c << KEY_SHIFTS[i]) | (c >>> (28 - KEY_SHIFTS[i]))) & 0xfffffff0;
            d = ((d << KEY_SHIFTS[i]) | (d >>> (28 - KEY_SHIFTS[i]))) & 0xfffffff0;
            int target = mode == DECRYPT ? 15 - i : i;
            java.util.Arrays.fill(schedule[target], (byte) 0);
            for (int k = 0; k < 24; k++) {
                schedule[target][k / 8] |= intBitRight(c, KEY_COMPRESSION[k], 7 - k % 8);
            }
            for (int k = 24; k < 48; k++) {
                schedule[target][k / 8] |= intBitRight(d, KEY_COMPRESSION[k] - 27, 7 - k % 8);
            }
        }
    }

    private static void crypt(byte[] input, byte[] output, byte[][] key) {
        int[] state = {permuted(input, IP_LEFT), permuted(input, IP_RIGHT)};
        for (int round = 0; round < 15; round++) {
            int oldRight = state[1];
            state[1] = f(state[1], key[round]) ^ state[0];
            state[0] = oldRight;
        }
        state[0] = f(state[1], key[15]) ^ state[0];
        inversePermutation(state, output);
    }

    private static int permuted(byte[] input, int[] positions) {
        int result = 0;
        for (int i = 0; i < positions.length; i++) result |= bit(input, positions[i], 31 - i);
        return result;
    }

    private static void inversePermutation(int[] state, byte[] output) {
        int[] outIndices = {3,2,1,0,7,6,5,4};
        int[] bitOffsets = {7,6,5,4,3,2,1,0};
        for (int i = 0; i < 8; i++) {
            int b = bitOffsets[i];
            output[outIndices[i]] = (byte) (intBitRight(state[1], b, 7)
                    | intBitRight(state[0], b, 6)
                    | intBitRight(state[1], b + 8, 5)
                    | intBitRight(state[0], b + 8, 4)
                    | intBitRight(state[1], b + 16, 3)
                    | intBitRight(state[0], b + 16, 2)
                    | intBitRight(state[1], b + 24, 1)
                    | intBitRight(state[0], b + 24, 0));
        }
    }

    private static int f(int state, byte[] key) {
        int t1 = intBitLeft(state,31,0) | ((state & 0xf0000000) >>> 1)
                | intBitLeft(state,4,5) | intBitLeft(state,3,6)
                | ((state & 0x0f000000) >>> 3) | intBitLeft(state,8,11)
                | intBitLeft(state,7,12) | ((state & 0x00f00000) >>> 5)
                | intBitLeft(state,12,17) | intBitLeft(state,11,18)
                | ((state & 0x000f0000) >>> 7) | intBitLeft(state,16,23);
        int t2 = intBitLeft(state,15,0) | ((state & 0x0000f000) << 15)
                | intBitLeft(state,20,5) | intBitLeft(state,19,6)
                | ((state & 0x00000f00) << 13) | intBitLeft(state,24,11)
                | intBitLeft(state,23,12) | ((state & 0x000000f0) << 11)
                | intBitLeft(state,28,17) | intBitLeft(state,27,18)
                | ((state & 0x0000000f) << 9) | intBitLeft(state,0,23);
        int x0 = ((t1 >>> 24) & 0xff) ^ (key[0] & 0xff);
        int x1 = ((t1 >>> 16) & 0xff) ^ (key[1] & 0xff);
        int x2 = ((t1 >>> 8) & 0xff) ^ (key[2] & 0xff);
        int x3 = ((t2 >>> 24) & 0xff) ^ (key[3] & 0xff);
        int x4 = ((t2 >>> 16) & 0xff) ^ (key[4] & 0xff);
        int x5 = ((t2 >>> 8) & 0xff) ^ (key[5] & 0xff);
        int substituted = SBOX[0][sboxIndex(x0 >>> 2)] << 28
                | SBOX[1][sboxIndex(((x0 & 3) << 4) | (x1 >>> 4))] << 24
                | SBOX[2][sboxIndex(((x1 & 15) << 2) | (x2 >>> 6))] << 20
                | SBOX[3][sboxIndex(x2 & 63)] << 16
                | SBOX[4][sboxIndex(x3 >>> 2)] << 12
                | SBOX[5][sboxIndex(((x3 & 3) << 4) | (x4 >>> 4))] << 8
                | SBOX[6][sboxIndex(((x4 & 15) << 2) | (x5 >>> 6))] << 4
                | SBOX[7][sboxIndex(x5 & 63)];
        int[] p = {15,6,19,20,28,11,27,16,0,14,22,25,4,17,30,9,1,7,23,13,31,26,2,8,18,12,29,5,21,10,3,24};
        int result = 0;
        for (int i = 0; i < p.length; i++) result |= intBitLeft(substituted, p[i], i);
        return result;
    }

    private static int bit(byte[] value, int bit, int shift) {
        int byteValue = value[(bit / 32 * 4 + 3 - bit % 32 / 8)] & 0xff;
        return ((byteValue >>> (7 - bit % 8)) & 1) << shift;
    }

    private static int intBitRight(int value, int bit, int shift) {
        return ((value >>> (31 - bit)) & 1) << shift;
    }

    private static int intBitLeft(int value, int bit, int shift) {
        return ((value << bit) & 0x80000000) >>> shift;
    }

    private static int sboxIndex(int value) {
        return (value & 0x20) | ((value & 0x1f) >>> 1) | ((value & 1) << 4);
    }
}
