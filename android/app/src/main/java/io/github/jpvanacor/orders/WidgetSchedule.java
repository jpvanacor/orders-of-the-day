package io.github.jpvanacor.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the block list the app hands over and works out what the widget shows. No Android imports, so it runs in plain tests. */
public final class WidgetSchedule {
    public static final class Block {
        public final long start;
        public final long end;
        public final String color;
        public final String title;
        public final String detail;

        Block(long start, long end, String color, String title, String detail) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.title = title;
            this.detail = detail;
        }
    }

    public static final class Now {
        public final Block current;
        public final Block next;

        Now(Block current, Block next) {
            this.current = current;
            this.next = next;
        }
    }

    private WidgetSchedule() {}

    /** One block per line: start|end|color|title|detail, times in epoch milliseconds. */
    public static List<Block> parse(String data) {
        List<Block> out = new ArrayList<>();
        if (data == null || data.isEmpty()) return out;
        for (String line : data.split("\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 4) continue;
            try {
                long s = Long.parseLong(p[0].trim());
                long e = Long.parseLong(p[1].trim());
                if (e <= s) continue;
                out.add(new Block(s, e, p[2], p[3], p.length > 4 ? p[4] : ""));
            } catch (NumberFormatException ignored) {
                // skip a damaged line
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    public static Now at(List<Block> blocks, long now) {
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.start <= now && now < b.end) return new Now(b, i + 1 < blocks.size() ? blocks.get(i + 1) : null);
        }
        return new Now(null, null);
    }

    /** The next moment the widget has to redraw: the soonest block start or end after now, or 0 when nothing is left. */
    public static long nextChange(List<Block> blocks, long now) {
        long best = 0;
        for (Block b : blocks) {
            if (b.start > now && (best == 0 || b.start < best)) best = b.start;
            if (b.end > now && (best == 0 || b.end < best)) best = b.end;
        }
        return best;
    }
}
