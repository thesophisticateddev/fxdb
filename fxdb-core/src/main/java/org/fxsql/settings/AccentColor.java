package org.fxsql.settings;

public enum AccentColor {
    BLUE("#0096FF", "#0096FF", "#007ACC", "#005A9E"),
    GREEN("#00A86B", "#00A86B", "#008C5A", "#006B45"),
    ORANGE("#FF8C00", "#FF8C00", "#E67E00", "#CC7000"),
    PURPLE("#8A2BE2", "#8A2BE2", "#7B24C7", "#6A1FA8"),
    RED("#E81123", "#E81123", "#C50F1E", "#A60D19"),
    YELLOW("#FFB900", "#FFB900", "#E6A700", "#CC9500");

    private final String fg, emphasis, muted, subtle;

    AccentColor(String fg, String emphasis, String muted, String subtle) {
        this.fg = fg;
        this.emphasis = emphasis;
        this.muted = muted;
        this.subtle = subtle;
    }

    public String getFg() { return fg; }
    public String getEmphasis() { return emphasis; }
    public String getMuted() { return muted; }
    public String getSubtle() { return subtle; }
}