package com.tntbbp.myminecraft.model;

import org.bukkit.Material;

/** 하나의 가상 주식 종목. */
public class Stock {

    private final String id;
    private final String name;
    private final Material material;
    private final double minPrice;
    private final double maxChangePercent;

    private double price;
    private double previousPrice;

    public Stock(String id, String name, Material material, double price, double minPrice, double maxChangePercent) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.price = price;
        this.previousPrice = price;
        this.minPrice = minPrice;
        this.maxChangePercent = maxChangePercent;
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

    public double getMaxChangePercent() {
        return maxChangePercent;
    }

    /** 가격을 랜덤 변동시킨다. */
    public void fluctuate() {
        previousPrice = price;
        double changePercent = (Math.random() * 2 - 1) * maxChangePercent / 100.0;
        double newPrice = price * (1 + changePercent);
        price = Math.round(Math.max(newPrice, minPrice) * 100.0) / 100.0;
    }

    public double changePercentFromPrevious() {
        if (previousPrice == 0) {
            return 0;
        }
        return ((price - previousPrice) / previousPrice) * 100.0;
    }
}
