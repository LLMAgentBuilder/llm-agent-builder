package io.github.llmagentbuilder.core.tool

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.llmagentbuilder.core.MapToObject
import io.github.llmagentbuilder.core.ToolConfig
import io.github.llmagentbuilder.core.observation.AgentToolExecutionObservationContext
import io.github.llmagentbuilder.core.observation.AgentToolExecutionObservationDocumentation
import io.github.llmagentbuilder.core.observation.DefaultAgentToolExecutionObservationConvention
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.ai.model.function.DefaultFunctionCallbackBuilder
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.core.GenericTypeResolver
import java.util.*
import java.util.function.Supplier
import kotlin.streams.asSequence

interface AgentToolsProvider : Supplier<Map<String, AgentTool<*, *>>>

class AgentToolWrappersProvider(
    private val agentToolsProvider: AgentToolsProvider,
    private val observationRegistry: ObservationRegistry? = null,
) :
    Supplier<Map<String, FunctionCallback>> {
    override fun get(): Map<String, FunctionCallback> {
        val objectMapper =
            ObjectMapper().registerModule(KotlinModule.Builder().build())
        return agentToolsProvider.get().mapValues { (_, tool) ->
            val types =
                GenericTypeResolver.resolveTypeArguments(
                    tool.javaClass,
                    AgentTool::class.java
                )
            InstrumentedFunctionCallbackWrapper(
                DefaultFunctionCallbackBuilder().function(tool.name(), tool)
                    .schemaType(FunctionCallback.SchemaType.JSON_SCHEMA)
                    .description(tool.description())
                    .inputType(
                        types?.get(0)
                            ?: throw IllegalArgumentException("Bad type")
                    )
                    .objectMapper(objectMapper)
                    .build(), observationRegistry
            )
        }
    }

    private class InstrumentedFunctionCallbackWrapper(
        private val functionCallback: FunctionCallback,
        private val observationRegistry: ObservationRegistry? = null
    ) : FunctionCallback {
        override fun getName(): String {
            return functionCallback.name
        }

        override fun getDescription(): String {
            return functionCallback.description
        }

        override fun getInputTypeSchema(): String {
            return functionCallback.inputTypeSchema
        }

        override fun call(functionInput: String): String {
            val action = { functionCallback.call(functionInput) }
            return observationRegistry?.let { registry ->
                instrumentedCall(functionInput, action, registry)
            } ?: action.invoke()
        }

        private fun instrumentedCall(
            input: String,
            action: () -> String,
            registry: ObservationRegistry
        ): String {
            val observationContext =
                AgentToolExecutionObservationContext(name, input)
            val observation =
                AgentToolExecutionObservationDocumentation.AGENT_TOOL_EXECUTION.observation(
                    null,
                    DefaultAgentToolExecutionObservationConvention(),
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
    }

}

class CompositeAgentToolsProvider(private val providers: List<AgentToolsProvider>) :
    AgentToolsProvider {
    override fun get(): Map<String, AgentTool<*, *>> {
        return providers.flatMap { it.get().values }
            .distinctBy { it.name() }
            .associateBy { it.name() }
    }
}

object AgentToolsProviderFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun create(tools: List<ToolConfig>): AgentToolsProvider {
        val (toConfig, noConfig) = ServiceLoader.load(AgentToolFactory::class.java)
            .stream()
            .map { it.get() }
            .asSequence()
            .partition { it is ConfigurableAgentToolFactory<*, *> }
        val noConfigTools = noConfig.map { it.create() }
        val toolsMap = tools.associateBy { it.id }.mapValues { it.value.config }
        val toConfigTools = toConfig.map {
            val toolId =
                (it as ConfigurableAgentToolFactory<*, *>).toolId()
            val types =
                GenericTypeResolver.resolveTypeArguments(
                    it.javaClass,
                    ConfigurableAgentToolFactory::class.java
                )
            val configType =
                types?.get(0) ?: throw IllegalArgumentException("Invalid type")
            val instance =
                MapToObject.toObject(configType, toolsMap[toolId])
            val method = it.javaClass.getDeclaredMethod("create", configType)
            method.invoke(it, instance) as AgentTool<*, *>
        }
        val agentTools = (noConfigTools + toConfigTools)
            .associateBy { it.name() }.also {
                logger.info("Found agent tools {}", it.keys)
            };
        return object : AgentToolsProvider {
            override fun get(): Map<String, AgentTool<*, *>> {
                return agentTools
            }
        }
    }
}

object AutoDiscoveredAgentToolsProvider : AgentToolsProvider {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val agentTools: Map<String, AgentTool<*, *>> by lazy {
        ServiceLoader.load(AgentToolFactory::class.java)
            .stream()
            .map { it.get() }
            .map { it.create() }
            .asSequence()
            .associateBy { it.name() }.also {
                logger.info("Found agent tools {}", it.keys)
            }
    }

    override fun get(): Map<String, AgentTool<*, *>> {
        return agentTools
    }
}