package net.ch4rlesexe.economy.livingshop;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ManualEconomyCsv {
    public static final String[] HEADERS = {
            "Shop", "Product", "Item",
            "Buy Price", "Sell Price", "Stock", "Base Price",
            "S/D Rate", "Supply", "Demand",
            "Purchased", "Sold", "Holdover", "Bonus", "Base", "Week",
            "Player Count", "To Date"
    };

    private static final DecimalFormat MONEY;
    private static final DecimalFormat PLAIN;
    private static final DecimalFormat RATE;

    static {
        DecimalFormatSymbols us = DecimalFormatSymbols.getInstance(Locale.US);
        MONEY = new DecimalFormat("$#,##0.00", us);
        MONEY.setGroupingUsed(false);
        PLAIN = new DecimalFormat("0.##", us);
        PLAIN.setGroupingUsed(false);
        RATE = new DecimalFormat("0.00", us);
        RATE.setGroupingUsed(false);
    }

    private ManualEconomyCsv() {}

    public static List<ManualEconomyRow> read(Path path) throws IOException {
        List<ManualEconomyRow> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                return rows;
            }
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = parseLine(headerLine);
            Map<String, Integer> firstIdx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String h = normalizeHeader(headers.get(i));
                firstIdx.putIfAbsent(h, i);
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseLine(line);
                ManualEconomyRow row = new ManualEconomyRow();
                row.shop = cell(cols, firstIdx, "shop");
                row.product = cell(cols, firstIdx, "product");
                row.item = cell(cols, firstIdx, "item");
                row.buyPrice = parseMoney(cell(cols, firstIdx, "buy price"));
                row.sellPrice = parseMoney(cell(cols, firstIdx, "sell price"));
                row.stock = parseInt(cell(cols, firstIdx, "stock"));
                row.basePrice = parseMoney(cell(cols, firstIdx, "base price"));
                row.stockPrice = parseMoney(cell(cols, firstIdx, "stock price"));
                row.sdRate = parseDouble(cell(cols, firstIdx, "s/d rate"));
                row.supply = parseInt(cell(cols, firstIdx, "supply"));
                row.demand = parseDouble(cell(cols, firstIdx, "demand"));
                row.purchased = parseInt(cell(cols, firstIdx, "purchased"));
                row.sold = parseInt(cell(cols, firstIdx, "sold"));
                row.holdover = parseInt(cell(cols, firstIdx, "holdover"));
                row.bonus = parseDouble(cell(cols, firstIdx, "bonus"));
                row.base = parseDouble(cell(cols, firstIdx, "base"));
                row.week = parseInt(cell(cols, firstIdx, "week"));
                row.playerCount = parseInt(cell(cols, firstIdx, "player count"));
                row.toDate = parseInt(cell(cols, firstIdx, "to date"));
                if (!row.hasIdentity()) continue;
                rows.add(row);
            }
        }
        return rows;
    }

    public static void write(Path path, List<ManualEconomyRow> rows) throws IOException {
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(String.join(",", HEADERS));
            bw.newLine();
            for (ManualEconomyRow r : rows) {
                bw.write(escape(nullToEmpty(r.shop)));
                bw.write(',');
                bw.write(escape(nullToEmpty(r.product)));
                bw.write(',');
                bw.write(escape(nullToEmpty(r.item)));
                bw.write(',');
                bw.write(escape(formatMoney(r.buyPrice)));
                bw.write(',');
                bw.write(escape(formatMoney(r.sellPrice)));
                bw.write(',');
                bw.write(escape(formatInt(r.stock)));
                bw.write(',');
                bw.write(escape(formatMoney(r.basePrice != null ? r.basePrice : r.buyPrice)));
                bw.write(',');
                bw.write(escape(formatRate(r.sdRate)));
                bw.write(',');
                bw.write(escape(formatInt(r.supply != null ? r.supply : 1)));
                bw.write(',');
                bw.write(escape(formatPlain(r.demand != null ? r.demand : 1.0)));
                bw.write(',');
                bw.write(escape(formatInt(r.purchased != null ? r.purchased : 0)));
                bw.write(',');
                bw.write(escape(formatInt(r.sold != null ? r.sold : 0)));
                bw.write(',');
                bw.write(escape(formatInt(r.holdover)));
                bw.write(',');
                bw.write(escape(formatPlain(r.bonus != null ? r.bonus : 1.0)));
                bw.write(',');
                bw.write(escape(formatPlain(r.base)));
                bw.write(',');
                bw.write(escape(formatInt(r.week)));
                bw.write(',');
                bw.write(escape(formatInt(r.playerCount != null ? r.playerCount : 0)));
                bw.write(',');
                bw.write(escape(formatInt(r.toDate != null ? r.toDate : 0)));
                bw.newLine();
            }
        }
    }

    public static Double parseMoney(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        s = s.replace("$", "").replace(",", "").replace("\"", "").trim();
        if (s.isEmpty() || s.equals("-")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace(",", "").replace("\"", "");
        if (s.isEmpty() || s.equals("-")) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDouble(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace(",", "").replace("\"", "").replace("$", "");
        if (s.isEmpty() || s.equals("-")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatMoney(Double v) {
        if (v == null) return "";
        return MONEY.format(v);
    }

    public static String formatPriceAmount(double v) {
        return PLAIN.format(v);
    }

    private static String formatPlain(Double v) {
        if (v == null) return "";
        return PLAIN.format(v);
    }

    private static String formatRate(Double v) {
        if (v == null) return "1.00";
        return RATE.format(v);
    }

    private static String formatInt(Integer v) {
        if (v == null) return "";
        return Integer.toString(v);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeHeader(String h) {
        return h == null ? "" : h.trim().toLowerCase(Locale.ROOT);
    }

    private static String cell(List<String> cols, Map<String, Integer> idx, String header) {
        Integer i = idx.get(header);
        if (i == null || i < 0 || i >= cols.size()) return "";
        return cols.get(i);
    }


    public static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String escape(String value) {
        if (value == null) return "";
        boolean need = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!need) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
