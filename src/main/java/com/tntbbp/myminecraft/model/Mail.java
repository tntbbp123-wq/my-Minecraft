package com.tntbbp.myminecraft.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 우편 한 통. 시간은 전부 UTC epoch ms.
 *
 * <ul>
 *   <li>{@link #isHeld()}: 우편함이 가득 차 보류된 우편. 우편함에 보이지 않고 받을 수 없으며,
 *       공간이 나면 {@link #release(long)}로 우편함에 들어온다.</li>
 *   <li>{@link #isClaimed()}: 첨부(아이템·G)를 전부 받은 우편. 첨부가 없는 우편은 한 번 열면 받은 것으로 친다.
 *       받은 우편도 만료 시각까지는 기록용으로 남는다(웹 우편 탭).</li>
 *   <li>부분 수령: 인벤토리가 부족하면 들어간 만큼만 받고 {@code claimed=false}로 남는다.
 *       첨부 G는 첫 수령 때 한 번에 들어간다.</li>
 * </ul>
 */
public final class Mail {

    private final long id;
    private final MailSenderType senderType;
    private final String senderName;
    private final String title;
    private final String body;
    private final List<MailAttachment> attachments;
    private final long attachedG;
    private boolean gClaimed;
    private final long createdAt;
    private long expiresAt;
    private boolean claimed;
    private long claimedAt;
    private boolean held;

    public Mail(long id, MailSenderType senderType, String senderName, String title, String body,
                List<MailAttachment> attachments, long attachedG, boolean gClaimed, long createdAt, long expiresAt,
                boolean claimed, long claimedAt, boolean held) {
        this.id = id;
        this.senderType = senderType == null ? MailSenderType.SYSTEM : senderType;
        this.senderName = senderName == null || senderName.isBlank() ? this.senderType.label() : senderName;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.attachments = new ArrayList<>(attachments == null ? List.of() : attachments);
        this.attachedG = Math.max(0L, attachedG);
        this.gClaimed = gClaimed;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.claimed = claimed;
        this.claimedAt = claimed ? claimedAt : 0L;
        this.held = held && !claimed;
    }

    public long id() {
        return id;
    }

    public MailSenderType senderType() {
        return senderType;
    }

    public String senderName() {
        return senderName;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    /** 첨부 목록(수정 불가 뷰). 각 첨부의 수령 수량은 {@link #applyDelivery}로만 바뀐다. */
    public List<MailAttachment> attachments() {
        return Collections.unmodifiableList(attachments);
    }

    public long attachedG() {
        return attachedG;
    }

    /** 첨부 G를 이미 받았는지. */
    public boolean isGClaimed() {
        return gClaimed;
    }

    /** 아직 받지 않은 G (받았거나 첨부가 없으면 0). */
    public long pendingG() {
        return gClaimed ? 0L : attachedG;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isClaimed() {
        return claimed;
    }

    /** 다 받은 시각. 아직이면 0. */
    public long claimedAt() {
        return claimedAt;
    }

    public boolean isHeld() {
        return held;
    }

    /** 우편함에 들어 있고 아직 다 받지 않은 우편(상한 계산 대상). */
    public boolean isActive() {
        return !claimed && !held;
    }

    public boolean isExpired(long now) {
        return expiresAt <= now;
    }

    /** 아이템이나 G 첨부가 있는지. */
    public boolean hasRewards() {
        if (attachedG > 0) {
            return true;
        }
        for (MailAttachment attachment : attachments) {
            if (attachment.count() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 첨부를 전부 받았는지(첨부가 없으면 true). */
    public boolean isFullyDelivered() {
        if (pendingG() > 0) {
            return false;
        }
        for (MailAttachment attachment : attachments) {
            if (attachment.remaining() > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 이번 수령 결과를 반영한다. 다 받았으면 {@code claimed=true}.
     *
     * @param delivered  i번째 첨부에서 이번에 받은 수량 (길이가 짧으면 나머지는 0)
     * @param gDelivered 이번에 첨부 G를 받았는지
     * @return 다 받았으면 true
     */
    public boolean applyDelivery(int[] delivered, boolean gDelivered, long now) {
        if (delivered != null) {
            for (int i = 0; i < attachments.size() && i < delivered.length; i++) {
                attachments.get(i).addClaimed(delivered[i]);
            }
        }
        if (gDelivered) {
            gClaimed = true;
        }
        if (!claimed && isFullyDelivered()) {
            claimed = true;
            claimedAt = now;
            held = false;
        }
        return claimed;
    }

    /**
     * 보류를 풀고 우편함에 넣는다. 보관 기간은 원래 기간(만료 − 보낸 시각)을 지금부터 다시 센다.
     */
    public void release(long now) {
        if (!held) {
            return;
        }
        long retention = Math.max(0L, expiresAt - createdAt);
        held = false;
        expiresAt = now + retention;
    }
}
