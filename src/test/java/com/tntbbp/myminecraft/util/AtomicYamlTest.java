package com.tntbbp.myminecraft.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AtomicYamlTest {

    @TempDir
    Path dir;

    private static YamlConfiguration sample() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("54988c7d-0000-0000-0000-000000000000", 15230.5);
        data.set("teams.붉은늑대.name", "붉은늑대");
        data.set("teams.붉은늑대.members", List.of("a", "b"));
        return data;
    }

    @Test
    void writesSameBytesAsBukkitSave() throws IOException {
        YamlConfiguration data = sample();
        File atomic = dir.resolve("atomic.yml").toFile();
        File plain = dir.resolve("plain.yml").toFile();
        AtomicYaml.save(data, atomic);
        data.save(plain);
        assertArrayEquals(Files.readAllBytes(plain.toPath()), Files.readAllBytes(atomic.toPath()));
    }

    @Test
    void replacesExistingFileAndLeavesNoTempFiles() throws IOException {
        File target = dir.resolve("economy.yml").toFile();
        Files.writeString(target.toPath(), "old: true\n");
        YamlConfiguration data = sample();
        AtomicYaml.save(data, target);

        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(target);
        assertEquals(15230.5, reloaded.getDouble("54988c7d-0000-0000-0000-000000000000"));
        assertEquals("붉은늑대", reloaded.getString("teams.붉은늑대.name"));
        assertEquals(false, reloaded.contains("old"));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("economy.yml"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void createsMissingParentDirectory() throws IOException {
        Path nested = dir.resolve("a/b/homes.yml");
        AtomicYaml.save(sample(), nested.toFile());
        assertEquals(sample().saveToString(), Files.readString(nested));
    }
}
