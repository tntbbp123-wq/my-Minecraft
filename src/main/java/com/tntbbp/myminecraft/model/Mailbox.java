package com.tntbbp.myminecraft.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 플레이어 한 명의 우편함({@code plugins/MyMinecraft/mail/<uuid>.yml}). 우편은 id 오름차순(오래된 순)으로 둔다.
 * Bukkit에 의존하지 않는 순수 로직(만료 정리·상한·보류 해제)만 담는다.
 */
public final class Mailbox {

    private final UUID owner;
    private final List<Mail> mails = new ArrayList<>();

    public Mailbox(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    /** 전체 우편(보류·수령 완료 포함, id 오름차순, 수정 불가 뷰). */
    public List<Mail> mails() {
        return Collections.unmodifiableList(mails);
    }

    public boolean isEmpty() {
        return mails.isEmpty();
    }

    /** 우편을 넣는다(id 순서 유지). 같은 id가 이미 있으면 바꿔 넣는다. */
    public void add(Mail mail) {
        mails.removeIf(existing -> existing.id() == mail.id());
        mails.add(mail);
        mails.sort(Comparator.comparingLong(Mail::id));
    }

    public Mail find(long id) {
        for (Mail mail : mails) {
            if (mail.id() == id) {
                return mail;
            }
        }
        return null;
    }

    public boolean remove(long id) {
        return mails.removeIf(mail -> mail.id() == id);
    }

    /** 우편함에 있고 아직 다 받지 않은 우편 수(관리자 우편 포함). */
    public int activeCount() {
        int count = 0;
        for (Mail mail : mails) {
            if (mail.isActive()) {
                count++;
            }
        }
        return count;
    }

    /** 받지 않은 일반(관리자 외) 우편 수 — 일반 우편 상한 계산 기준. */
    public int regularActiveCount() {
        int count = 0;
        for (Mail mail : mails) {
            if (mail.isActive() && !mail.senderType().isAdmin()) {
                count++;
            }
        }
        return count;
    }

    /** 새 우편을 이 우편함에 바로 넣을 수 있는지(아니면 보류). */
    public boolean hasRoom(MailSenderType type, MailLimits limits) {
        return limits.hasRoom(type, regularActiveCount(), activeCount());
    }

    public int heldCount() {
        int count = 0;
        for (Mail mail : mails) {
            if (mail.isHeld()) {
                count++;
            }
        }
        return count;
    }

    /** 받을 수 있는 우편(오래된 순). */
    public List<Mail> active() {
        List<Mail> result = new ArrayList<>();
        for (Mail mail : mails) {
            if (mail.isActive()) {
                result.add(mail);
            }
        }
        return result;
    }

    /** 만료된 우편(보류·수령 완료 포함)을 지운다. @return 지운 개수 */
    public int purgeExpired(long now) {
        int before = mails.size();
        mails.removeIf(mail -> mail.isExpired(now));
        return before - mails.size();
    }

    /** 만료된 우편이 있는지(지우지는 않음). */
    public boolean hasExpired(long now) {
        for (Mail mail : mails) {
            if (mail.isExpired(now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 우편함에 공간이 나는 만큼 보류 우편을 오래된 순으로 넣는다.
     *
     * @return 이번에 우편함에 들어온 우편
     */
    public List<Mail> releaseHeld(long now, MailLimits limits) {
        List<Mail> released = new ArrayList<>();
        for (Mail mail : mails) {
            if (mail.isHeld() && hasRoom(mail.senderType(), limits)) {
                mail.release(now);
                released.add(mail);
            }
        }
        return released;
    }

    /** 가장 큰 우편 id (없으면 0). */
    public long maxId() {
        long max = 0L;
        for (Mail mail : mails) {
            max = Math.max(max, mail.id());
        }
        return max;
    }
}
