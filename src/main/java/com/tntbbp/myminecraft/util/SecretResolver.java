package com.tntbbp.myminecraft.util;

import com.tntbbp.myminecraft.MyMinecraftPlugin;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * config.yml에 적힌 비밀값(디스코드 봇 토큰 등)을 실제 값으로 풀어준다.
 *
 * <ul>
 *   <li>{@code env:이름} — 환경변수에서 읽는다. 값이 어느 파일에도 남지 않아 가장 안전하다.</li>
 *   <li>{@code file:경로} — 다른 파일에서 읽는다. config를 통째로 보여줘도 값은 노출되지 않는다.</li>
 *   <li>{@code enc:...} — 데이터 폴더의 {@code secret.key}로 복호화한다.</li>
 *   <li>그 외 — 적힌 값을 그대로 쓴다 (기존 평문 설정과 호환).</li>
 * </ul>
 *
 * <p><b>enc:는 난독화지 보안이 아니다.</b> 플러그인이 재시작 때마다 자동으로 풀 수 있어야 하므로
 * 키가 같은 서버 안에 있어야 하고, 서버 파일을 통째로 얻은 상대에게는 소용이 없다. 설정 파일을
 * 남에게 보여주거나 백업이 돌아다닐 때 눈에 띄지 않게 하는 용도이며, 확실한 보관은 env: 쪽이다.
 */
public class SecretResolver {

    public static final String ENV_PREFIX = "env:";
    public static final String FILE_PREFIX = "file:";
    public static final String ENC_PREFIX = "enc:";

    private static final String KEY_FILE_NAME = "secret.key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_BITS = 256;

    private final MyMinecraftPlugin plugin;

    public SecretResolver(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** 이미 env:/file:/enc: 형태로 감춰둔 값인지. */
    public boolean isManaged(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        return value.startsWith(ENV_PREFIX) || value.startsWith(FILE_PREFIX) || value.startsWith(ENC_PREFIX);
    }

    /**
     * 설정값을 실제 비밀값으로 푼다. 풀 수 없으면 빈 문자열을 반환해서, 해당 기능이 서버를
     * 죽이지 않고 조용히 비활성화되도록 한다.
     */
    public String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();

        if (value.startsWith(ENV_PREFIX)) {
            String name = value.substring(ENV_PREFIX.length()).trim();
            String fromEnv = System.getenv(name);
            if (fromEnv == null || fromEnv.isBlank()) {
                plugin.getLogger().warning("환경변수 " + name + " 가 비어있어 해당 기능을 사용할 수 없습니다.");
                return "";
            }
            return fromEnv.trim();
        }

        if (value.startsWith(FILE_PREFIX)) {
            Path path = Path.of(value.substring(FILE_PREFIX.length()).trim());
            try {
                return Files.readString(path, StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                plugin.getLogger().warning("비밀값 파일을 읽지 못했습니다 (" + path + "): " + e.getMessage());
                return "";
            }
        }

        if (value.startsWith(ENC_PREFIX)) {
            try {
                return decrypt(value.substring(ENC_PREFIX.length()).trim());
            } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
                plugin.getLogger().warning("암호화된 비밀값을 풀지 못했습니다. "
                        + KEY_FILE_NAME + " 가 그대로인지 확인하세요: " + e.getMessage());
                return "";
            }
        }

        return value;
    }

    /** 평문을 config.yml에 적을 수 있는 enc: 형식으로 바꾼다. */
    public String encrypt(String plain) throws GeneralSecurityException, IOException {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    private String decrypt(String encoded) throws GeneralSecurityException, IOException {
        byte[] combined = Base64.getDecoder().decode(encoded);
        if (combined.length <= IV_LENGTH) {
            throw new IllegalArgumentException("암호문이 너무 짧습니다.");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    /**
     * 데이터 폴더의 secret.key를 읽고, 없으면 새로 만든다.
     * 이 파일이 있어야 enc: 값을 풀 수 있으므로 백업/공유 시 함께 새어나가지 않도록 주의해야 한다.
     */
    private SecretKey key() throws GeneralSecurityException, IOException {
        File keyFile = new File(plugin.getDataFolder(), KEY_FILE_NAME);
        if (keyFile.exists()) {
            String encoded = Files.readString(keyFile.toPath(), StandardCharsets.UTF_8).trim();
            return new SecretKeySpec(Base64.getDecoder().decode(encoded), "AES");
        }

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_BITS);
        SecretKey generated = generator.generateKey();

        plugin.getDataFolder().mkdirs();
        Files.writeString(keyFile.toPath(), Base64.getEncoder().encodeToString(generated.getEncoded()),
                StandardCharsets.UTF_8);
        restrictToOwner(keyFile);

        plugin.getLogger().info(KEY_FILE_NAME + " 를 새로 만들었습니다. 이 파일이 있어야 enc: 값을 풀 수 있으니 "
                + "서버 밖으로 유출되지 않게 주의하세요.");
        return generated;
    }

    /** 키 파일을 소유자만 읽고 쓸 수 있게 한다 (지원하지 않는 파일시스템에서는 무시된다). */
    private void restrictToOwner(File file) {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }
}
