package io.github.llmagentbuilder.core.planner

import io.github.llmagentbuilder.core.AgentFinish
import io.github.llmagentbuilder.core.IntermediateAgentStep
import io.github.llmagentbuilder.core.Planner
import io.github.llmagentbuilder.core.chatmemory.ChatMemory
import io.github.llmagentbuilder.core.chatmemory.ChatMemoryProvider
import io.github.llmagentbuilder.core.chatmemory.ChatMemoryStore
import io.github.llmagentbuilder.core.config.AgentConfig
import io.github.llmagentbuilder.core.executor.ActionPlanningResult
import io.github.llmagentbuilder.core.observation.AgentPlanningObservationContext
import io.github.llmagentbuilder.core.observation.AgentPlanningObservationDocumentation
import io.github.llmagentbuilder.core.observation.DefaultAgentPlanningObservationConvention
import io.github.llmagentbuilder.core.observation.InstrumentedChatModel
import io.github.llmagentbuilder.core.planner.simple.SimpleOutputParser
import io.github.llmagentbuilder.core.tool.AgentTool
import io.github.llmagentbuilder.core.tool.AgentToolsProvider
import io.github.llmagentbuilder.core.tool.AutoDiscoveredAgentToolsProvider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.prompt.PromptTemplate
import java.util.*

interface LLMPlannerChatMemoryProvider {
    fun provide(
        chatMemoryStore: ChatMemoryStore,
        inputs: Map<String, Any>
    ): ChatMemory?

    companion object {
        val DEFAULT = object : LLMPlannerChatMemoryProvider {
            override fun provide(
                chatMemoryStore: ChatMemoryStore,
                inputs: Map<String, Any>
            ): ChatMemory? {
                return inputs["memory_id"]?.let { memoryId ->
                    if (memoryId.toString().trim().isNotBlank())
                        ChatMemoryProvider.DEFAULT.provideChatMemory(
                            chatMemoryStore,
                            memoryId.toString()
                        )
                    else null
                }
            }
        }
    }
}

open class LLMPlanner(
    private val chatModel: ChatModel,
    private val chatOptions: ChatOptions,
    private val toolsProvider: AgentToolsProvider,
    private val outputParser: OutputParser,
    private val userPromptTemplate: PromptTemplate,
    private val systemPromptTemplate: PromptTemplate? = null,
    private val systemInstruction: String? = null,
    private val chatMemoryStore: ChatMemoryStore? = null,
    private val chatMemoryProvider: LLMPlannerChatMemoryProvider? = null,
    private val chatHistoryCustomizer: ChatHistoryCustomizer? = null,
    private val observationRegistry: ObservationRegistry? = null,
    private val meterRegistry: MeterRegistry? = null,
    private val stopSequence: List<String>? = null,
) : Planner {

    private val chatClient = ChatClient.create(chatModel)

    override fun plan(
        inputs: Map<String, Any>,
        intermediateSteps: List<IntermediateAgentStep>
    ): ActionPlanningResult {
        val action = { internalPlan(inputs, intermediateSteps) }
        return observationRegistry?.let { registry ->
            instrumentedPlan(inputs, action, registry)
        } ?: action.invoke()
    }

    private fun internalPlan(
        inputs: Map<String, Any>,
        intermediateSteps: List<IntermediateAgentStep>
    ): ActionPlanningResult {
        val systemInstruction = systemInstruction ?: ""
        val thoughts = constructScratchpad(intermediateSteps)
        val tools = toolsProvider.get()
        val toolNames = tools.keys
        val context = inputs + mutableMapOf(
            "system_instruction" to systemInstruction,
            "agent_scratchpad" to thoughts,
            "tools" to renderTools(tools.values),
            "tool_names" to toolNames.joinToString(", ")
        )
        val messages = mutableListOf(userPromptTemplate.createMessage(context))
        systemPromptTemplate?.run {
            messages.add(0, SystemMessage(systemPromptTemplate.render(context)))
        }

        val chatMemory = chatMemoryStore?.let { store ->
            chatMemoryProvider?.provide(store, inputs)
        }

        chatMemory?.let { memory ->
            messages.forEach(memory::add)
        }
        val prompt = Prompt(
            chatMemory?.messages()
                ?.let { chatHistoryCustomizer?.customize(it) ?: it }
                ?: messages,
            prepareChatClientOptions(toolNames)
        )
        val response = chatClient.prompt(prompt).call().chatResponse()
        val text = response.result?.output?.content?.trim() ?: ""
        if (text.isEmpty()) {
            return ActionPlanningResult.finish(
                AgentFinish.fromOutput(
                    "No response from LLM",
                    text
                )
            )
        }
        chatMemory?.add(response.result.output)
        val result = outputParser.parse(text)
        return ActionPlanningResult.fromParseResult(result)
    }

    private fun instrumentedPlan(
        input: Map<String, Any>,
        action: () -> ActionPlanningResult,
        registry: ObservationRegistry
    ): ActionPlanningResult {
        val observationContext =
            AgentPlanningObservationContext(input)
        val observation =
            AgentPlanningObservationDocumentation.AGENT_PLANNING.observation(
                null,
                DefaultAgentPlanningObservationConvention(),
                { observationContext },
                registry
            ).start()
        return try {
            observation.openScope().use {
                val response = action.invoke()
                observationContext.setResponse(response)
                response
            }
        } catch (e: Exception) {
            observation.error(e)
            throw e
        } finally {
            observation.stop()
        }
    }

    private fun constructScratchpad(intermediateSteps: List<IntermediateAgentStep>): String {
        return intermediateSteps.joinToString(" ") {
            val (action, observation) = it
            "${action.log} \nObservation: $observation\n"
        }
    }

    private fun renderTools(tools: Collection<AgentTool<*, *>>): String {
        return tools.joinToString("\n") {
            "${it.name()}: ${it.description()}"
        }
    }

    private fun prepareChatClientOptions(
        toolNames: Set<String>
    ): ChatOptions {
        return ServiceLoader.load(ChatOptionsConfigurer::class.java)
            .firstOrNull { it.supports(chatOptions) }?.configure(
                chatOptions, ChatOptionsConfigurer.ChatOptionsConfig(
                    toolNames, stopSequence
                )
            ) ?: chatOptions
    }

    override fun toString(): String {
        return "LLMPlanner(outputParser=${outputParser.javaClass.simpleName})"
    }

    class Builder {
        private lateinit var chatModel: ChatModel
        private lateinit var chatOptions: ChatOptions
        private var toolsProvider: AgentToolsProvider? = null
        private var outputParser: OutputParser = SimpleOutputParser.INSTANCE
        private var observationRegistry: ObservationRegistry? = null
        private var meterRegistry: MeterRegistry? = null
        private var userPromptTemplate: PromptTemplate =
            PromptTemplate("{input}")
        private var systemPromptTemplate: PromptTemplate =
            PromptTemplate("{{system_instruction}}")
        private var systemInstruction: String? = null
        private var chatMemoryStore: ChatMemoryStore? = null
        private var chatMemoryProvider: ChatMemoryProvider? = null
        private var chatHistoryCustomizer: ChatHistoryCustomizer? = null
        private var stopSequence: List<String>? = null

        fun withChatModel(chatModel: ChatModel): Builder {
            this.chatModel = chatModel
            return this
        }

        fun withChatOptions(chatOptions: ChatOptions): Builder {
            this.chatOptions = chatOptions
            return this
        }

        fun withAgentToolsProvider(toolsProvider: AgentToolsProvider?): Builder {
            this.toolsProvider = toolsProvider
            return this
        }

        fun withOutputParser(outputParser: OutputParser): Builder {
            this.outputParser = outputParser
            return this
        }

        fun withObservationRegistry(observationRegistry: ObservationRegistry?): Builder {
            this.observationRegistry = observationRegistry
            return this
        }

        fun withMeterRegistry(meterRegistry: MeterRegistry?): Builder {
            this.meterRegistry = meterRegistry
            return this
        }

        fun withUserPromptTemplate(userPromptTemplate: PromptTemplate): Builder {
            this.userPromptTemplate = userPromptTemplate
            return this
        }

        fun withSystemPromptTemplate(systemPromptTemplate: PromptTemplate): Builder {
            this.systemPromptTemplate = systemPromptTemplate
            return this
        }

        fun withSystemInstruction(systemInstruction: String?): Builder {
            if (systemInstruction != null) {
                this.systemInstruction = systemInstruction
            }
            return this
        }

        fun withChatMemoryStore(chatMemoryStore: ChatMemoryStore?): Builder {
            this.chatMemoryStore = chatMemoryStore
            return this
        }

        fun withChatMemoryProvider(chatMemoryProvider: ChatMemoryProvider): Builder {
            this.chatMemoryProvider = chatMemoryProvider
            return this
        }

        fun withChatHistoryCustomizer(chatHistoryCustomizer: ChatHistoryCustomizer?): Builder {
            this.chatHistoryCustomizer = chatHistoryCustomizer
            return this
        }

        fun withStopSequence(stopSequence: List<String>?): Builder {
            this.stopSequence = stopSequence
            return this
        }

        fun build(): LLMPlanner {
            if (!::chatModel.isInitialized) {
                throw IllegalArgumentException("ChatClient is required")
            }
            val chatModel =
                if (chatModel is InstrumentedChatModel) chatModel else InstrumentedChatModel(
                    chatModel, observationRegistry, meterRegistry
                )
            return LLMPlanner(
                chatModel,
                chatOptions,
                toolsProvider ?: AutoDiscoveredAgentToolsProvider,
                outputParser,
                userPromptTemplate,
                systemPromptTemplate,
                systemInstruction,
                chatMemoryStore,
                LLMPlannerChatMemoryProvider.DEFAULT,
                chatHistoryCustomizer,
                observationRegistry,
                meterRegistry,
                stopSequence,
            )
        }
    }
}

abstract class LLMPlannerFactory {
    abstract fun defaultBuilder(): LLMPlanner.Builder

    fun create(agentConfig: AgentConfig): LLMPlanner {
        val (chatClient, chatOptions) = agentConfig.llmConfig
        val (_, systemInstruction) = agentConfig.plannerConfig()
        val (agentToolsProvider) = agentConfig.toolsConfig()
        val (chatMemoryStore, chatHistoryCustomizer) = agentConfig.memoryConfig()
        val (observationRegistry, meterRegistry) = agentConfig.observationConfig()
        return create(
            chatClient,
            chatOptions,
            agentToolsProvider,
            systemInstruction,
            chatMemoryStore,
            chatHistoryCustomizer,
            observationRegistry,
            meterRegistry
        )
    }

    fun create(
        chatModel: ChatModel,
        chatOptions: ChatOptions,
        agentToolsProvider: AgentToolsProvider? = null,
        systemInstruction: String? = null,
        chatMemoryStore: ChatMemoryStore? = null,
        chatHistoryCustomizer: ChatHistoryCustomizer? = null,
        observationRegistry: ObservationRegistry? = null,
        meterRegistry: MeterRegistry? = null,
    ): LLMPlanner {
        return defaultBuilder()
            .withChatModel(chatModel)
            .withChatOptions(chatOptions)
            .withAgentToolsProvider(agentToolsProvider)
            .withSystemInstruction(systemInstruction)
            .withChatMemoryStore(chatMemoryStore)
            .withChatHistoryCustomizer(chatHistoryCustomizer)
            .withObservationRegistry(observationRegistry)
            .withMeterRegistry(meterRegistry)
            .build()
    }
}