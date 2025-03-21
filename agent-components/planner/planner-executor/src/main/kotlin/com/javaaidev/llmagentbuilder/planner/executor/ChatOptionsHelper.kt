package com.javaaidev.llmagentbuilder.planner.executor

import com.javaaidev.llmagentbuilder.core.ChatOptionsConfigurer
import org.springframework.ai.chat.prompt.ChatOptions
import java.util.*
import kotlin.streams.asSequence

object ChatOptionsHelper {
    fun buildChatOptions(
        chatOptions: ChatOptions?,
        chatOptionsConfig: ChatOptionsConfigurer.ChatOptionsConfig
    ): ChatOptions? {
        return ServiceLoader.load(ChatOptionsConfigurer::class.java)
            .stream()
            .map { it.get() }
            .asSequence()
            .filter { it.supports(chatOptions) }
            .firstOrNull()?.configure(chatOptions, chatOptionsConfig)
    }
}