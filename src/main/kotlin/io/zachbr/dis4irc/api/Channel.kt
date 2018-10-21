package io.zachbr.dis4irc.api

data class Channel(val name: String, val discordId: Long?, val type: Type) {
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
