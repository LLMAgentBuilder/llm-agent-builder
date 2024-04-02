package cc.vividcode.ai.agentappbuilder.core

import cc.vividcode.ai.agentappbuilder.core.executor.AgentExecutor
import org.slf4j.LoggerFactory

object AgentFactory {
    private val logger = LoggerFactory.getLogger(javaClass)
    fun <REQUEST : AgentRequest, RESPONSE> create(
        name: String,
        description: String,
        planner: Planner,
        responseFactory: (Map<String, Any>) -> RESPONSE
    ): Agent<REQUEST, RESPONSE> {
        val executor = AgentExecutor(planner, AgentTools.agentToolWrappers)
        return object : Agent<REQUEST, RESPONSE> {
            override fun name() = name

            override fun description() = description

            override fun call(request: REQUEST): RESPONSE {
                logger.info(
                    "Start executing agent {} with request {}",
                    name(),
                    request
                )
                return responseFactory(executor.call(request.toMap())).also {
                    logger.info(
                        "Finished executing agent {} with response {}",
                        name(),
                        it
                    )
                }
            }
        }
    }
}