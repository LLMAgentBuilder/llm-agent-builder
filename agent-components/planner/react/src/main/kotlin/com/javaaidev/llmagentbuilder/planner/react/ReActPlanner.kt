package com.javaaidev.llmagentbuilder.planner.react

import com.javaaidev.llmagentbuilder.planner.executor.LLMPlanExecutor
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.client.ChatClient

class ReActPlanner(
    chatClient: ChatClient,
    observationRegistry: ObservationRegistry? = null,
) :
    LLMPlanExecutor(chatClient, ReActOutputParser.INSTANCE, observationRegistry)
