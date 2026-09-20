package eu.tango.scamscreener.marketguard.auction;

/** Which of the price signals the HUD and the auction warnings compare against; both always use the same one. */
public enum ReferencePriceBasis {
    /** Only the current Lowest BIN (the default); the median when the item has none. */
    LOWEST_BIN,
    /** Always the median of Lowest BIN, 7-day and 30-day average. */
    MEDIAN,
    /** The median while the signals agree, otherwise the signal in the player's favour (highest when buying, lowest when listing). */
    FAVOURABLE
}
