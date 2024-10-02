package io.github.llmagentbuilder.planner.structuredchat

import io.github.llmagentbuilder.core.MapToObject
import io.github.llmagentbuilder.core.Planner
import io.github.llmagentbuilder.core.PlannerProvider
import org.springframework.ai.chat.client.ChatClient

class StructuredChatPlannerProvider : PlannerProvider {
    override fun configKey(): String {
        return "structuredChat"
    }

    override fun providePlanner(
        chatClientBuilder: ChatClient.Builder,
        config: Map<String, Any?>?
    ): Planner? {
        val plannerConfig =
            MapToObject.toObject<StructuredChatPlannerConfig>(config)
        if (plannerConfig?.enabled == false) {
            return null
        }
        val chatClient =
            chatClientBuilder.defaultAdvisors(StructuredChatPromptAdvisor())
                .build()
        return StructuredChatPlanner(chatClient)
    }
}