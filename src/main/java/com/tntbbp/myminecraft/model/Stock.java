package com.tntbbp.myminecraft.model;

import com.tntbbp.myminecraft.manager.economy.StockRules;
import org.bukkit.Material;

/**
 * 하나의 가상 주식 종목.
 *
 * <p>이름·아이콘·가격 범위·변동 단위·업종은 관리자가 바꿀 수 있다(웹 관리 {@code PATCH /stocks/{id}}).
 * {@code halted}(거래 중지)면 매매와 정기 변동을 하지 않는다. {@code revision}은 정의가 바뀔 때마다
 * 1씩 올라가며, 웹에서 두 사람이 동시에 고칠 때 먼저 고친 쪽을 덮어쓰지 않도록 비교하는 데 쓴다.
 * 정기 변동·뉴스 반영처럼 가격만 바뀌는 경우에는 올리지 않는다.
 */
public class Stock {

    private final String id;
    private final boolean custom;
    private String name;
    private Material material;
    private double minPrice;
    private double maxPrice;
    private double maxChangePercent;
    private double changeUnit;
    private String businessType;

    private double price;
    private double previousPrice;
    private boolean halted;
    private int revision = 1;

    /** 기본 제공 종목 (config.yml의 stock.list, 퍼센트 기반 변동). */
    public Stock(String id, String name, Material material, double price, double minPrice, double maxChangePercent, String businessType) {
        this(id, name, material, price, minPrice, 0.0, maxChangePercent, 0.0, false, businessType);
    }

    /** 관리자가 추가한 종목 (최소/최대값 + 변동 단위 기반 변동). */
    public Stock(String id, String name, Material material, double price, double minPrice, double maxPrice, double changeUnit, String businessType) {
        this(id, name, material, price, minPrice, maxPrice, 0.0, changeUnit, true, businessType);
    }

    private Stock(String id, String name, Material material, double price, double minPrice, double maxPrice,
                  double maxChangePercent, double changeUnit, boolean custom, String businessType) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.price = price;
        this.previousPrice = price;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.maxChangePercent = maxChangePercent;
        this.changeUnit = changeUnit;
        this.custom = custom;
        this.businessType = businessType != null ? businessType : "";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Material getMaterial() {
        return material;
    }

    public double getPrice() {
        return price;
    }

    public double getPreviousPrice() {
        return previousPrice;
    }

    public double getMinPrice() {
        return minPrice;
    }

    /** 0이면 상한 없음(퍼센트 기반 종목). */
    public double getMaxPrice() {
        return maxPrice;
    }

    public double getMaxChangePercent() {
        return maxChangePercent;
    }

    public double getChangeUnit() {
        return changeUnit;
    }

    public boolean isCustom() {
        return custom;
    }

    /** AI 뉴스 생성 시 참고하는 업종/하는 일 설명. */
    public String getBusinessType() {
        return businessType;
    }

    /** 거래 중지 상태. 중지된 종목은 매매할 수 없고 정기 변동도 하지 않는다. */
    public boolean isHalted() {
        return halted;
    }

    /** 정의가 바뀔 때마다 1씩 오르는 번호(동시 수정 충돌 감지용). */
    public int getRevision() {
        return revision;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    /** 가격 범위를 바꾼다. 검증은 호출하는 쪽({@link StockRules#validateDefinition})에서 한다. 현재가는 건드리지 않는다. */
    public void setPriceRange(double minPrice, double maxPrice) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public void setChangeUnit(double changeUnit) {
        this.changeUnit = changeUnit;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType != null ? businessType : "";
    }

    public void setHalted(boolean halted) {
        this.halted = halted;
    }

    /** 저장된 revision을 복원한다(1 미만이면 1). */
    public void restoreRevision(int revision) {
        this.revision = Math.max(1, revision);
    }

    public void bumpRevision() {
        revision++;
    }

    /** 재시작 후 저장된 가격을 복원한다. */
    public void restoreState(double price, double previousPrice) {
        this.price = price;
        this.previousPrice = previousPrice;
    }

    /**
     * 관리자가 가격을 직접 정한다(가격 범위로 맞추고 소수 둘째 자리까지). 직전 가격은 바뀌기 전 가격이 된다.
     *
     * @return 실제로 적용된 가격
     */
    public double applyManualPrice(double newPrice) {
        previousPrice = price;
        price = StockRules.round2(StockRules.clamp(newPrice, minPrice, maxPrice));
        return price;
    }

    /** 현재가가 가격 범위 밖이면 범위 안으로 맞춘다. 바뀌었으면 true(직전 가격은 바뀌기 전 가격이 된다). */
    public boolean clampPriceToRange() {
        double clamped = StockRules.clamp(price, minPrice, maxPrice);
        if (clamped == price) {
            return false;
        }
        previousPrice = price;
        price = clamped;
        return true;
    }

    /** 가격을 랜덤 변동시킨다. */
    public void fluctuate() {
        previousPrice = price;
        double newPrice;
        if (changeUnit > 0) {
            int steps = 1 + (int) (Math.random() * 5);
            double sign = Math.random() < 0.5 ? -1 : 1;
            newPrice = price + sign * steps * changeUnit;
            newPrice = Math.max(minPrice, newPrice);
            if (maxPrice > 0) {
                newPrice = Math.min(maxPrice, newPrice);
            }
            newPrice = minPrice + Math.round((newPrice - minPrice) / changeUnit) * changeUnit;
        } else {
            double changePercent = (Math.random() * 2 - 1) * maxChangePercent / 100.0;
            newPrice = price * (1 + changePercent);
            newPrice = Math.round(Math.max(newPrice, minPrice) * 100.0) / 100.0;
        }
        price = Math.max(minPrice, newPrice);
    }

    /** 관리자가 작성한 뉴스에 따라 가격을 직접 변동시킨다 (%). */
    public void applyNewsImpact(double percent) {
        // 입력에서 막지만, 이미 저장돼 있던 뉴스까지 막으려고 여기서도 본다.
        // NaN이면 Math.round(NaN)이 0이라 주가가 0이 되고(공짜 매수), Infinity면 주가가 폭주한다.
        if (!Double.isFinite(percent)) {
            return;
        }
        double newPrice = price * (1 + percent / 100.0);
        if (!Double.isFinite(newPrice)) {
            return;
        }
        previousPrice = price;
        newPrice = Math.max(minPrice, newPrice);
        if (maxPrice > 0) {
            newPrice = Math.min(maxPrice, newPrice);
        }
        price = Math.round(newPrice * 100.0) / 100.0;
    }

    public double changePercentFromPrevious() {
        if (previousPrice == 0) {
            return 0;
        }
        return ((price - previousPrice) / previousPrice) * 100.0;
    }
}
