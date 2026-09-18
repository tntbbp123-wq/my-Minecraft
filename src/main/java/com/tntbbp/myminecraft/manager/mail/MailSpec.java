package com.tntbbp.myminecraft.manager.mail;

import com.tntbbp.myminecraft.model.MailSenderType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 보낼 우편 한 통의 내용({@link MailManager#send}의 입력). 받는 사람마다 같은 내용으로 한 통씩 만들어진다.
 *
 * <pre>
 * // 예: 퀘스트 보상(나중에 P-11에서 사용)
 * MailSpec spec = MailSpec.of(MailSenderType.QUEST, "일일 퀘스트", "일일 퀘스트 보상", "수고하셨습니다!",
 *         List.of(MailSpec.Item.of(enhanceStone)), 500);
 * plugin.getMailManager().enqueue(spec, List.of(player.getUniqueId()));
 * </pre>
 *
 * @param senderType    보낸 곳 (system|admin|quest). 관리자 우편은 상한 제외·보관 기간이 길다.
 * @param senderName    보낸 사람 표시 이름 (null이면 종류 기본값: 시스템/운영자/퀘스트)
 * @param title         제목 (비면 안 됨)
 * @param body          내용 (null 가능)
 * @param items         첨부 아이템 (null 가능)
 * @param attachedG     첨부 G (0 이상)
 * @param retentionDays 보관 일수. 0 이하면 종류 기본값(config mail.*-retention-days)
 * @param actor         기록용 주체(관리자 우편이면 웹 사용자 이름). null이면 "system"
 */
public record MailSpec(MailSenderType senderType, String senderName, String title, String body, List<Item> items,
                       long attachedG, int retentionDays, String actor) {

    public MailSpec {
        if (senderType == null) {
            senderType = MailSenderType.SYSTEM;
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("우편 제목이 비어 있습니다.");
        }
        if (attachedG < 0) {
            throw new IllegalArgumentException("첨부 G는 0 이상이어야 합니다.");
        }
        items = items == null ? List.of() : List.copyOf(items);
        body = body == null ? "" : body;
    }

    /** 보관 기간·기록 주체를 기본값으로 둔 간단 생성. */
    public static MailSpec of(MailSenderType senderType, String senderName, String title, String body,
                              List<Item> items, long attachedG) {
        return new MailSpec(senderType, senderName, title, body, items, attachedG, 0, null);
    }

    /**
     * 첨부 아이템 한 종류.
     *
     * @param template    아이템 견본 (수량은 무시하고 1개로 저장)
     * @param count       보낼 수량 (1 이상, 한 칸 최대 수량보다 커도 됨 — 받을 때 나눠 들어감)
     * @param itemName    특수 아이템명(/특수아이템소환 이름). 바닐라면 null
     * @param displayName 표시 이름. null이면 아이템 이름(없으면 재질 이름)
     */
    public record Item(ItemStack template, int count, String itemName, String displayName) {

        public Item {
            if (template == null || template.getType().isAir()) {
                throw new IllegalArgumentException("첨부 아이템이 비어 있습니다.");
            }
            if (count < 1) {
                throw new IllegalArgumentException("첨부 수량은 1 이상이어야 합니다.");
            }
        }

        /** 이 아이템 묶음 그대로(수량 = 묶음 수량). */
        public static Item of(ItemStack stack) {
            return new Item(stack, Math.max(1, stack.getAmount()), null, null);
        }

        public static Item of(ItemStack template, int count) {
            return new Item(template, count, null, null);
        }

        /** 특수 아이템({@code SpecialItemCatalog} 이름). */
        public static Item special(String itemName, ItemStack template, int count, String displayName) {
            return new Item(template, count, itemName, displayName);
        }
    }
}
