package me.bechberger.minicli;

final class UsageEx extends Exception {
    final Object cmd;
    final boolean help;
    final boolean version;

    UsageEx(Object cmd, String message) {
        super(message);
        this.cmd = cmd;
        this.help = false;
        this.version = false;
    }

    private UsageEx(Object cmd, boolean help, boolean version) {
        super("");
        this.cmd = cmd;
        this.help = help;
        this.version = version;
    }

    static UsageEx help(Object cmd) {
        return new UsageEx(cmd, true, false);
    }

    static UsageEx version() {
        return new UsageEx(null, false, true);
    }
}