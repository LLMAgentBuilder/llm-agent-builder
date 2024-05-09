package io.github.llmagentbuilder.core.planner

import org.springframework.ai.chat.prompt.ChatOptions

/**
 * Configure [ChatOptions]
 */
interface ChatOptionsConfigurer {

    data class ChatOptionsConfig(
        val functions: Set<String>? = null,
        val stopSequence: List<String>? = null,
    )

    /**
     * Checks if the [ChatOptions] can be configured
     * @param chatOptions Default [ChatOptions]
     */
    fun supports(chatOptions: ChatOptions): Boolean

    /**
     * Should return a new [ChatOptions]
     *
     * @param chatOptions Default [ChatOptions]
     * @param config configuration
     * @return A new [ChatOptions]
     */
    fun configure(
        chatOptions: ChatOptions,
        config: ChatOptionsConfig
    ): ChatOptions
}