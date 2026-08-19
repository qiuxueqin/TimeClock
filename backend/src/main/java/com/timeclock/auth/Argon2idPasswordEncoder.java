package com.timeclock.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id 密码编码器（DEC-01，REQ-AUTH-02）。
 *
 * <p>使用 Argon2id 变体对明文密码做加盐哈希，输出 PHC 格式字符串
 * {@code $argon2id$v=19$m=…,t=…,p=…$<salt>$<hash>}，可安全存库并用于校验。
 *
 * <p>参数采用 OWASP 推荐下限（后续可在配置中调参）：内存 64 MiB（65536 KiB）、
 * 迭代 3 次、并行度 4、盐 16 字节、输出哈希 32 字节。
 *
 * <p>BCrypt/SCrypt 不在 V1.0 技术基线内；统一使用本实现，禁止明文密码（backend §10.1）。
 */
public class Argon2idPasswordEncoder implements PasswordEncoder {

    public static final String PHC_PREFIX = "$argon2id$";
    private static final int MEMORY_KI_B = 65536; // 64 MiB
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 4;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int VERSION = 19;

    private final SecureRandom random;

    public Argon2idPasswordEncoder() {
        this.random = new SecureRandom();
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return encode(rawPassword, salt);
    }

    /** 校验：能解析 PHC 字符串则比对哈希，否则返回 false（不泄露具体失败原因）。 */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        Phc parsed = Phc.parse(encodedPassword);
        if (parsed == null) {
            return false;
        }
        byte[] candidate = hash(rawPassword, parsed.salt,
                parsed.memoryKiB, parsed.iterations, parsed.parallelism, parsed.hashLength);
        return constantTimeEquals(candidate, parsed.hash);
    }

    /** 构造随机盐对应的 PHC 字符串。 */
    private String encode(CharSequence rawPassword, byte[] salt) {
        byte[] hash = hash(rawPassword, salt, MEMORY_KI_B, ITERATIONS, PARALLELISM, HASH_BYTES);
        return PHC_PREFIX + "v=" + VERSION
                + "$m=" + MEMORY_KI_B + ",t=" + ITERATIONS + ",p=" + PARALLELISM
                + "$" + Base64.encode(salt) + "$" + Base64.encode(hash);
    }

    private byte[] hash(CharSequence rawPassword, byte[] salt,
                        int memoryKiB, int iterations, int parallelism, int hashLength) {
        byte[] passwordBytes = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[hashLength];
        generator.generateBytes(passwordBytes, out, 0, out.length);
        return out;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigests.constantTimeEquals(a, b);
    }

    /** 解析 PHC Argon2 字符串；不支持的格式返回 null。 */
    static final class Phc {
        byte[] salt;
        byte[] hash;
        int memoryKiB;
        int iterations;
        int parallelism;
        int hashLength;

        static Phc parse(String encoded) {
            if (encoded == null || !encoded.startsWith(PHC_PREFIX)) {
                return null;
            }
            String rest = encoded.substring(PHC_PREFIX.length());
            String[] parts = rest.split("\\$");
            // 段结构：version | params | salt | hash
            if (parts.length != 4) {
                return null;
            }
            Phc p = new Phc();
            if (!parseVersion(parts[0])) {
                return null;
            }
            if (!parseParams(parts[1], p)) {
                return null;
            }
            byte[] salt = Base64.decode(parts[2]);
            byte[] hash = Base64.decode(parts[3]);
            if (salt == null || hash == null) {
                return null;
            }
            p.salt = salt;
            p.hash = hash;
            p.hashLength = hash.length;
            return p;
        }

        private static boolean parseVersion(String s) {
            return s.startsWith("v=") && s.substring(2).equals(String.valueOf(VERSION));
        }

        private static boolean parseParams(String s, Phc p) {
            try {
                for (String kv : s.split(",")) {
                    String[] e = kv.split("=");
                    if (e.length != 2) {
                        return false;
                    }
                    switch (e[0]) {
                        case "m" -> p.memoryKiB = Integer.parseInt(e[1]);
                        case "t" -> p.iterations = Integer.parseInt(e[1]);
                        case "p" -> p.parallelism = Integer.parseInt(e[1]);
                        default -> { /* 忽略未知参数 */ }
                    }
                }
            } catch (NumberFormatException ex) {
                return false;
            }
            return p.memoryKiB > 0 && p.iterations > 0 && p.parallelism > 0;
        }
    }

    /** 常量时间比较（避免时间侧信道泄漏哈希差异）。 */
    private static final class MessageDigests {
        static boolean constantTimeEquals(byte[] a, byte[] b) {
            if (a.length != b.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < a.length; i++) {
                result |= (a[i] ^ b[i]);
            }
            return result == 0;
        }
    }

    /** URL 安全 Base64（与 PHC 标准一致：'-' '_' 无填充）。 */
    static final class Base64 {
        static final char[] ALPHABET =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
        static final int[] VALUES = new int[128];

        static {
            Arrays.fill(VALUES, -1);
            for (int i = 0; i < ALPHABET.length; i++) {
                VALUES[ALPHABET[i]] = i;
            }
        }

        static String encode(byte[] data) {
            StringBuilder sb = new StringBuilder((data.length * 4 + 2) / 3);
            for (int i = 0; i < data.length; i += 3) {
                int b0 = data[i] & 0xff;
                int b1 = i + 1 < data.length ? data[i + 1] & 0xff : 0;
                int b2 = i + 2 < data.length ? data[i + 2] & 0xff : 0;
                int triple = (b0 << 16) | (b1 << 8) | b2;
                sb.append(ALPHABET[(triple >> 18) & 0x3f]);
                sb.append(ALPHABET[(triple >> 12) & 0x3f]);
                if (i + 1 < data.length) {
                    sb.append(ALPHABET[(triple >> 6) & 0x3f]);
                }
                if (i + 2 < data.length) {
                    sb.append(ALPHABET[triple & 0x3f]);
                }
            }
            return sb.toString();
        }

        static byte[] decode(String s) {
            if (s == null || s.isEmpty()) {
                return null;
            }
            int padding = s.length() % 4;
            // 无填充；len%4 的余数决定末尾字节数
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length() * 3 / 4 + 2);
            int bits = 0;
            int bitsCount = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= 128 || VALUES[c] < 0) {
                    return null;
                }
                bits = (bits << 6) | VALUES[c];
                bitsCount += 6;
                if (bitsCount >= 8) {
                    bitsCount -= 8;
                    out.write((bits >> bitsCount) & 0xff);
                }
            }
            return out.toByteArray();
        }
    }
}
