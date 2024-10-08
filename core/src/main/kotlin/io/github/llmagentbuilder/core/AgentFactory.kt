package io.github.llmagentbuilder.core

import io.github.llmagentbuilder.core.executor.AgentExecutor
import io.github.llmagentbuilder.core.tool.AgentToolWrappersProvider
import io.github.llmagentbuilder.core.tool.AgentToolsProvider
import io.github.llmagentbuilder.core.tool.AutoDiscoveredAgentToolsProvider
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory

object AgentFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createChatAgent(
        planner: Planner,
        name: String? = null,
        description: String? = null,
        usageInstruction: String? = null,
        agentToolsProvider: AgentToolsProvider? = null,
        id: String? = null,
        observationRegistry: ObservationRegistry? = null,
    ): ChatAgent {
        val agentName = name ?: "ChatAgent"
        val executor = createAgentExecutor(
            agentName,
            planner,
            agentToolsProvider ?: AutoDiscoveredAgentToolsProvider,
            observationRegistry
        )
        return ExecutableChatAgent(
            executor,
            agentName,
            description ?: "A conversational chat agent",
            usageInstruction ?: "Ask me anything",
            id,
            observationRegistry,
        ).also {
            logger.info(
                "Created ChatAgent [{}] with planner [{}]",
                agentName,
                planner
            )
        }
    }

    fun <REQUEST : AgentRequest, RESPONSE> create(
        name: String,
        description: String,
        usageInstruction: String,
        planner: Planner,
        responseFactory: (Map<String, Any>) -> RESPONSE,
        agentToolsProvider: AgentToolsProvider = AutoDiscoveredAgentToolsProvider,
        id: String? = null,
        observationRegistry: ObservationRegistry? = null,
    ): Agent<REQUEST, RESPONSE> {
        val executor = createAgentExecutor(
            name,
            planner,
            agentToolsProvider,
            observationRegistry
        )
        return ExecutableAgent(
            name,
            description,
            usageInstruction,
            executor,
            responseFactory,
            id,
            observationRegistry,
        )
    }

    private fun createAgentExecutor(
        agentName: String,
        planner: Planner,
        agentToolsProvider: AgentToolsProvider,
        observationRegistry: ObservationRegistry? = null,
    ): AgentExecutor {
        return AgentExecutor(
            agentName,
            planner,
            AgentToolWrappersProvider(
                agentToolsProvider,
                observationRegistry
            ).get(),
            observationRegistry = observationRegistry,
        )
    }

    private open class ExecutableAgent<REQUEST : AgentRequest, RESPONSE>(
        private val name: String,
        private val description: String,
        private val usageInstruction: String,
        private val executor: AgentExecutor,
        private val responseFactory: (Map<String, Any>) -> RESPONSE,
        private val id: String? = null,
        private val observationRegistry: ObservationRegistry? = null,
    ) :
        Agent<REQUEST, RESPONSE> {
        private val logger = LoggerFactory.getLogger("AgentExecutor")

        override fun id(): String {
            return id ?: super.id()
        }

        override fun name(): String {
            return name
        }

        override fun description(): String {
            return description
        }

        override fun usageInstruction(): String {
            return usageInstruction
        }

        override fun call(request: REQUEST): RESPONSE {
            logger.info(
                "Start executing agent [{}] with request [{}]",
                name(),
                request
            )
            val response = responseFactory(executor.call(request.toMap()))
            logger.info(
                "Finished executing agent [{}] with response [{}]",
                name(),
                response
            )
            return response
        }

    }

    private class ExecutableChatAgent(
        executor: AgentExecutor,
        name: String,
        description: String,
        usageInstruction: String,
        id: String? = null,
        observationRegistry: ObservationRegistry? = null,
    ) : ExecutableAgent<ChatAgentRequest, ChatAgentResponse>(
        name,
        description,
        usageInstruction,
        executor,
        ChatAgentResponse::fromMap,
        id,
        observationRegistry,
    ), ChatAgent
}