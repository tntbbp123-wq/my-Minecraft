package com.tntbbp.myminecraft.model;

import org.bukkit.Material;

/** 하나의 가상 주식 종목. */
public class Stock {

    private final String id;
    private final String name;
    private final Material material;
    private final double minPrice;
    private final double maxPrice;
    private final double maxChangePercent;
    private final double changeUnit;
    private final boolean custom;
    private final String businessType;

    private double price;
    private double previousPrice;

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

    /** 재시작 후 저장된 가격을 복원한다. */
    public void restoreState(double price, double previousPrice) {
        this.price = price;
        this.previousPrice = previousPrice;
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
        previousPrice = price;
        double newPrice = price * (1 + percent / 100.0);
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
