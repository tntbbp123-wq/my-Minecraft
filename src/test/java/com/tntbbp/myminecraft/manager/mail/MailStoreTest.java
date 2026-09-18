package com.tntbbp.myminecraft.manager.mail;

import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.model.Mailbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailStoreTest {

    @TempDir
    Path dir;

    private static final Logger LOGGER = Logger.getLogger("test");

    private static Mail mail(long id) {
        return new Mail(id, MailSenderType.SYSTEM, null, "제목" + id, "내용",
                List.of(), 0L, false, 0L, 1_000_000L, false, 0L, false);
    }

    @Test
    void saveThenLoadReturnsSavedContentBeforeFlush() {
        MailStore store = new MailStore(dir, LOGGER);
        UUID owner = UUID.randomUUID();
        Mailbox mailbox = new Mailbox(owner);
        mailbox.add(mail(1));
        store.saveMailbox(mailbox);

        Mailbox loaded = store.loadMailbox(owner);
        assertEquals(1, loaded.mails().size());
        assertEquals("제목1", loaded.mails().get(0).title());

        store.flush();
    }

    @Test
    void flushWritesFileAndNewStoreLoadsIt() {
        MailStore store = new MailStore(dir, LOGGER);
        UUID owner = UUID.randomUUID();
        Mailbox mailbox = new Mailbox(owner);
        mailbox.add(mail(1));
        store.saveMailbox(mailbox);
        store.flush();

        assertTrue(Files.exists(dir.resolve(owner + ".yml")));

        MailStore other = new MailStore(dir, LOGGER);
        Mailbox loaded = other.loadMailbox(owner);
        assertEquals(1, loaded.mails().size());
        other.flush();
    }

    @Test
    void ownersListsOnlyUuidNamedFiles() throws IOException {
        Files.writeString(dir.resolve("notes.yml"), "x: 1\n");
        Files.writeString(dir.resolve("counter.yml"), "next-id: 5\n");

        MailStore store = new MailStore(dir, LOGGER);
        UUID owner = UUID.randomUUID();
        store.saveMailbox(new Mailbox(owner));
        store.flush();

        assertEquals(List.of(owner), store.owners());
    }

    @Test
    void loadNextIdUsesSavedCounter() {
        MailStore store = new MailStore(dir, LOGGER);
        store.saveNextId(42L);
        store.flush();

        MailStore other = new MailStore(dir, LOGGER);
        assertEquals(42L, other.loadNextId());
        other.flush();
    }

    @Test
    void loadNextIdFallsBackToMaxMailIdWhenCounterMissing() {
        MailStore store = new MailStore(dir, LOGGER);
        UUID ownerA = UUID.randomUUID();
        Mailbox mailboxA = new Mailbox(ownerA);
        mailboxA.add(mail(5));
        mailboxA.add(mail(9));
        store.saveMailbox(mailboxA);

        UUID ownerB = UUID.randomUUID();
        Mailbox mailboxB = new Mailbox(ownerB);
        mailboxB.add(mail(3));
        store.saveMailbox(mailboxB);
        store.flush();

        MailStore other = new MailStore(dir, LOGGER);
        assertEquals(10L, other.loadNextId());
        other.flush();
    }

    @Test
    void loadNextIdIsOneWhenEmpty() {
        MailStore store = new MailStore(dir, LOGGER);
        assertEquals(1L, store.loadNextId());
        store.flush();
    }

    @Test
    void corruptMailboxLoadsEmptyAndBacksUpWhenRequested() throws IOException {
        MailStore store = new MailStore(dir, LOGGER);
        UUID owner = UUID.randomUUID();
        Path path = dir.resolve(owner + ".yml");
        Files.writeString(path, "mails: [unclosed\n");

        Mailbox loaded = store.loadMailbox(owner);
        assertTrue(loaded.isEmpty());

        boolean hasBackup;
        try (var stream = Files.list(dir)) {
            hasBackup = stream.anyMatch(p -> p.getFileName().toString().startsWith(owner + ".yml.broken-"));
        }
        assertTrue(hasBackup);

        store.flush();
    }

    @Test
    void corruptMailboxSkipsBackupWhenNotRequested() throws IOException {
        MailStore store = new MailStore(dir, LOGGER);
        UUID owner = UUID.randomUUID();
        Path path = dir.resolve(owner + ".yml");
        Files.writeString(path, "mails: [unclosed\n");

        Mailbox loaded = store.loadMailbox(owner, false);
        assertTrue(loaded.isEmpty());

        boolean hasBackup;
        try (var stream = Files.list(dir)) {
            hasBackup = stream.anyMatch(p -> p.getFileName().toString().contains(".broken-"));
        }
        assertFalse(hasBackup);

        store.flush();
    }
}
