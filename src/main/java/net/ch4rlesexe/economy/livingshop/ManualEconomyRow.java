package net.ch4rlesexe.economy.livingshop;

public final class ManualEconomyRow {
    public String shop = "";
    public String product = "";
    public String item = "";
    public Double buyPrice;
    public Double sellPrice;

    public Integer stock;
    public Double basePrice;

    public Double stockPrice;
    public Double sdRate;

    public Integer supply;
    public Double demand;
    public Integer purchased;
    public Integer sold;
    public Integer holdover;
    public Double bonus;

    public Double base;
    public Integer week;
    public Integer playerCount;
    public Integer toDate;

    public boolean hasIdentity() {
        if (shop != null && !shop.isBlank() && product != null && !product.isBlank()) {
            return true;
        }
        return item != null && !item.isBlank();
    }

    public String key() {
        if (shop != null && !shop.isBlank() && product != null && !product.isBlank()) {
            return shop.trim().toLowerCase() + "/" + product.trim();
        }
        return "item:" + (item == null ? "" : item.trim().toLowerCase());
    }


    public int resolveStockCap() {
        if (supply != null && supply < 0) return -1;
        if (stock != null && stock < 0) return -1;
        if (stock != null) return Math.max(0, stock);
        if (base != null) return Math.max(0, (int) Math.round(base));

        if (supply != null && supply != 1) return Math.max(0, supply);
        return -1;
    }
}
