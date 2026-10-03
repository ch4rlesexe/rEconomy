package net.ch4rlesexe.economy.livingshop;

public final class ShopProduct {
    public final String shop;
    public final String product;
    public final String material;
    public final String itemName;
    public final double buyPrice;
    public final double sellPrice;
    public final boolean hasBuy;
    public final boolean hasSell;

    public ShopProduct(String shop, String product, String material, String itemName,
                       double buyPrice, double sellPrice, boolean hasBuy, boolean hasSell) {
        this.shop = shop;
        this.product = product;
        this.material = material;
        this.itemName = itemName;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.hasBuy = hasBuy;
        this.hasSell = hasSell;
    }

    public String key() {
        return shop.toLowerCase() + "/" + product;
    }
}
