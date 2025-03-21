package com.javaaidev.llmagentbuilder.planner.reactjson

import com.javaaidev.llmagentbuilder.planner.executor.LLMPlanExecutor
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.client.ChatClient

class ReActJsonPlanner(
    chatClient: ChatClient,
    observationRegistry: ObservationRegistry? = null,
) :
    LLMPlanExecutor(
        chatClient,
        ReActJsonOutputParser.INSTANCE,
        observationRegistry
    )