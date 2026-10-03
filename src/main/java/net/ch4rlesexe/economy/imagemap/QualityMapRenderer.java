package net.ch4rlesexe.economy.imagemap;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Objects;
import javax.imageio.ImageIO;

public final class QualityMapRenderer extends MapRenderer {
    private final BufferedImage tile;
    private volatile boolean drawn;

    public QualityMapRenderer(BufferedImage tile) {
        super(false);
        this.tile = Objects.requireNonNull(tile);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (drawn) return;
        canvas.drawImage(0, 0, tile);
        map.setTrackingPosition(false);
        drawn = true;
    }

    public static BufferedImage load(String url) throws Exception {
        return ImageIO.read(URI.create(url).toURL());
    }


    public static BufferedImage[][] prepareTiles(BufferedImage source, int tiles, boolean dither) {
        int size = Math.max(1, tiles) * 128;
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double scale = Math.max(size / (double) source.getWidth(), size / (double) source.getHeight());
            int w = (int) Math.round(source.getWidth() * scale);
            int h = (int) Math.round(source.getHeight() * scale);
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            g.drawImage(source, x, y, w, h, null);
        } finally {
            g.dispose();
        }

        BufferedImage[][] out = new BufferedImage[tiles][tiles];
        for (int ty = 0; ty < tiles; ty++) {
            for (int tx = 0; tx < tiles; tx++) {
                BufferedImage slice = scaled.getSubimage(tx * 128, ty * 128, 128, 128);
                out[ty][tx] = toMapPaletteImage(slice, dither);
            }
        }
        return out;
    }


    @SuppressWarnings("deprecation")
    private static BufferedImage toMapPaletteImage(BufferedImage src, boolean dither) {
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        double[][] errR = new double[128][128];
        double[][] errG = new double[128][128];
        double[][] errB = new double[128][128];

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a < 16) {

                    out.setRGB(x, y, 0);
                    continue;
                }
                if (dither) {
                    r = clamp((int) Math.round(r + errR[y][x]));
                    g = clamp((int) Math.round(g + errG[y][x]));
                    b = clamp((int) Math.round(b + errB[y][x]));
                }
                byte matched = MapPalette.matchColor(r, g, b);
                java.awt.Color c = MapPalette.getColor(matched);
                out.setRGB(x, y, (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue());

                if (dither) {
                    double dr = r - c.getRed();
                    double dg = g - c.getGreen();
                    double db = b - c.getBlue();
                    diffuse(errR, x, y, dr);
                    diffuse(errG, x, y, dg);
                    diffuse(errB, x, y, db);
                }
            }
        }
        return out;
    }

    private static void diffuse(double[][] err, int x, int y, double delta) {

        if (x + 1 < 128) err[y][x + 1] += delta * 7.0 / 16.0;
        if (y + 1 < 128) {
            if (x > 0) err[y + 1][x - 1] += delta * 3.0 / 16.0;
            err[y + 1][x] += delta * 5.0 / 16.0;
            if (x + 1 < 128) err[y + 1][x + 1] += delta * 1.0 / 16.0;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
