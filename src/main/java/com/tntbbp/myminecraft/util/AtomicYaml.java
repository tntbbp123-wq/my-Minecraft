package com.tntbbp.myminecraft.util;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * YAML 데이터 파일을 "임시 파일에 다 쓴 뒤 한 번에 바꿔치기" 방식으로 저장한다.
 * {@code data.save(file)}은 파일을 먼저 비운 뒤 쓰기 때문에, 쓰는 도중 서버가 죽거나 웹 관리자가 그 순간
 * 파일을 읽으면 반쯤 쓰인 내용이 보일 수 있다. 저장되는 내용(바이트)은 {@code data.save(file)}과 같다.
 *
 * <p>임시 파일은 일반 파일과 같은 기본 권한으로 만들고(폴더의 기본 ACL도 그대로 상속), 기존 파일이 있으면
 * 그 권한을 옮겨 적용한다. 그래서 바꿔치기 후에도 다른 계정(웹 관리자 등)의 읽기 권한이 유지된다.
 */
public final class AtomicYaml {

    private AtomicYaml() {
    }

    /** temp 파일에 저장 후 원자적 이동(같은 디렉터리). REPLACE_EXISTING + ATOMIC_MOVE(미지원 FS는 일반 이동). */
    public static void save(FileConfiguration data, File target) throws IOException {
        write(data.saveToString(), target.toPath());
    }

    /** 문자열을 UTF-8로 {@code target}에 원자적으로 쓴다. */
    public static void write(String content, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path directory = absolute.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            copyPermissions(absolute, temp);
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** POSIX 파일시스템이면 기존 파일의 권한을 임시 파일에 옮긴다. 지원하지 않거나 실패하면 무시한다. */
    private static void copyPermissions(Path from, Path to) {
        if (!Files.exists(from)) {
            return;
        }
        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            // Windows 등 POSIX 권한이 없는 파일시스템
        }
    }
}
