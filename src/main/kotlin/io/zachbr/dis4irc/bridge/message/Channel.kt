package io.zachbr.dis4irc.bridge.message

data class Channel(
    /**
     * Name of the channel
     */
    val name: String,
    /**
     * Discord ID of the channel
     */
    val discordId: Long?,
    /**
     * Channel type
     */
    val type: Type
) {
    enum class Type {
        /**
         * Message originated in Discord
         */
        DISCORD,
        /**
         * Message originated in IRC
         */
        IRC
    }
}
