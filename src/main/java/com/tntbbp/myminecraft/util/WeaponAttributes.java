package com.tntbbp.myminecraft.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 전용 무기(레바테인/드라켄피어스/사인참사검/발뭉/자하신검)의 기본 속성을 붙이는 공용 헬퍼.
 *
 * <p>1.20.5부터는 아이템에 속성 수정자를 하나라도 직접 넣는 순간 attribute_modifiers 컴포넌트가
 * 생기면서 <b>재질의 기본 공격력·공격속도가 통째로 대체</b>된다. 그래서 공격력만 넣으면 재질이
 * 갖고 있던 공격속도(네더라이트 검 1.6, 삼지창 1.1)가 사라지고 맨손 속도(4.0)로 휘둘러지게 된다.
 * 여기서 공격속도 기본값을 명시적으로 복사해 넣어 아이템 설명에 적힌 수치와 실제가 맞게 만든다.
 */
public final class WeaponAttributes {

    private WeaponAttributes() {
    }

    /**
     * 최종 공격력이 {@code attackDamage}가 되도록 수정자를 붙이고, 재질의 기본 공격속도를 유지시킨다.
     *
     * @param attackDamageKey 이 무기 전용 공격력 수정자 키 (강화 시스템이 자기 수정자와 구분하는 기준)
     */
    public static void applyBase(ItemMeta meta, Material type, double attackDamage, NamespacedKey attackDamageKey) {
        applyBase(meta, type, attackDamage, 0, attackDamageKey);
    }

    /**
     * 공격속도까지 직접 정하는 판. 재질 기본값과 다른 속도를 쓰는 무기(모닝스타·장병기 등)에 쓴다.
     *
     * @param attackSpeed 0 이하면 재질의 기본 공격속도를 그대로 쓴다
     */
    public static void applyBase(ItemMeta meta, Material type, double attackDamage, double attackSpeed,
                                 NamespacedKey attackDamageKey) {
        // 맨손 기본 공격력(1.0)을 더하면 툴팁상 공격력이 attackDamage가 된다.
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                attackDamageKey,
                attackDamage - 1.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));

        if (attackSpeed > 0) {
            // 맨손 기본 공격속도(4.0)를 빼야 툴팁에 적힌 값이 그대로 나온다.
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(attackDamageKey.getNamespace(), attackDamageKey.getKey() + "_speed"),
                    attackSpeed - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            return;
        }
        for (AttributeModifier modifier : type.getDefaultAttributeModifiers(EquipmentSlot.HAND)
                .get(Attribute.GENERIC_ATTACK_SPEED)) {
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, modifier);
        }
    }
}
