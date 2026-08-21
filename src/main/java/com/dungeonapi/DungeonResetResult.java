package com.dungeonapi;

public class DungeonResetResult {

    private final int total;
    private final int successful;
    private final int failed;

    public DungeonResetResult(
            int total,
            int successful,
            int failed
    ) {
        this.total = total;
        this.successful = successful;
        this.failed = failed;
    }

    public int getTotal() {
        return total;
    }

    public int getSuccessful() {
        return successful;
    }

    public int getFailed() {
        return failed;
    }
}