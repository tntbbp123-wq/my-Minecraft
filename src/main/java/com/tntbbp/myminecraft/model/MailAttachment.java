package com.tntbbp.myminecraft.model;

/**
 * 우편 첨부 아이템 한 종류. 아이템 자체는 1개짜리 견본을 {@code ItemStack#serializeAsBytes}로 직렬화한
 * base64({@link #data()})로 보관하고, 수량은 {@link #count()}로 따로 센다(한 칸 최대 수량보다 많아도 된다).
 * 웹 표시·기록용 요약(재질·표시 이름·특수아이템명)을 함께 저장한다.
 *
 * <p>부분 수령: 인벤토리가 부족하면 들어간 만큼 {@link #claimed()}가 늘고 나머지는 우편에 남는다.
 */
public final class MailAttachment {

    private final String data;
    private final String material;
    private final String displayName;
    private final String itemName;
    private final int count;
    private int claimed;

    /**
     * @param data        견본 아이템(1개)의 {@code serializeAsBytes} base64
     * @param material    재질 이름 (예: DIAMOND)
     * @param displayName 표시용 이름 (특수 아이템은 한국어 이름, 바닐라는 재질 이름)
     * @param itemName    특수 아이템명(/특수아이템소환 이름). 바닐라 아이템이면 null
     * @param count       보낸 총 수량 (1 이상)
     * @param claimed     이미 받은 수량
     */
    public MailAttachment(String data, String material, String displayName, String itemName, int count,
                          int claimed) {
        this.data = data;
        this.material = material;
        this.displayName = displayName;
        this.itemName = itemName;
        this.count = Math.max(0, count);
        this.claimed = Math.max(0, Math.min(this.count, claimed));
    }

    public String data() {
        return data;
    }

    public String material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    /** 특수 아이템명. 바닐라 아이템이면 null. */
    public String itemName() {
        return itemName;
    }

    /** 보낸 총 수량. */
    public int count() {
        return count;
    }

    /** 이미 받은 수량. */
    public int claimed() {
        return claimed;
    }

    /** 아직 받지 않은 수량. */
    public int remaining() {
        return count - claimed;
    }

    /** 이번에 받은 수량을 더한다(총 수량을 넘지 않음). */
    public void addClaimed(int amount) {
        if (amount > 0) {
            claimed = (int) Math.min(count, (long) claimed + amount);
        }
    }

    /** 거래 기록({@code {"name","count"}})에 쓰는 이름: 특수 아이템명, 없으면 재질 이름. */
    public String logName() {
        return itemName != null ? itemName : material;
    }

    /** 수령 상태까지 그대로 복사. */
    public MailAttachment copy() {
        return new MailAttachment(data, material, displayName, itemName, count, claimed);
    }

    /** 아직 아무도 받지 않은 새 사본 (여러 명에게 같은 첨부를 보낼 때). */
    public MailAttachment fresh() {
        return new MailAttachment(data, material, displayName, itemName, count, 0);
    }
}
