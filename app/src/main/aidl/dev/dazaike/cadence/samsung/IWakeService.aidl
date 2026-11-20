package dev.dazaike.cadence.samsung;

/** Runs inside a Shizuku user service (shell uid). */
interface IWakeService {
    /** Shizuku's reserved "destroy" transaction. */
    void destroy() = 16777114;

    /**
     * Opens Samsung Health on a hidden virtual display, so it syncs from the watch without
     * anything appearing on screen, and closes it again. Returns true once a watch sync finished,
     * false if none finished within [timeoutMs].
     */
    boolean syncOnHiddenDisplay(String component, int timeoutMs) = 1;
}
