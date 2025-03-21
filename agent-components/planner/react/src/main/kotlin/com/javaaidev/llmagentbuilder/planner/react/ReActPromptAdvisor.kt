package com.javaaidev.llmagentbuilder.planner.react

import com.javaaidev.llmagentbuilder.planner.executor.ChatOptionsHelper
import com.javaaidev.llmagentbuilder.core.ChatOptionsConfigurer
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain
import org.springframework.core.Ordered

internal const val defaultSystemTextTemplate = """
{system_instruction}

You have access to the following tools:

{tools}

Use the following format:

Question: the input question you must answer
Thought: you should always think about what to do
Action: the action to take, should be one of [{tool_names}]
Action Input: the input to the action
Observation: the result of the action
... (this Thought/Action/Action Input/Observation can repeat N times)
Thought: I now know the final answer
Final Answer: the final answer to the original input question

Begin!
    
"""

internal const val defaultUserTextTemplate = """
{user_input}

{agent_scratchpad}
"""

class ReActPromptAdvisor : CallAroundAdvisor {
    override fun getName(): String {
        return javaClass.simpleName
    }

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE + 1000
    }

    override fun aroundCall(
        advisedRequest: AdvisedRequest,
        chain: CallAroundAdvisorChain
    ): AdvisedResponse {
        val systemParams = HashMap(advisedRequest.systemParams)
        systemParams["system_instruction"] = advisedRequest.systemText ?: ""
        val userParams = HashMap(advisedRequest.userParams)
        userParams["user_input"] = advisedRequest.userText
        val chatOptions = ChatOptionsHelper.buildChatOptions(
            advisedRequest.chatOptions,
            ChatOptionsConfigurer.ChatOptionsConfig(listOf("\\nObservation"))
        )
        val builder = AdvisedRequest.from(advisedRequest)
            .systemText(defaultSystemTextTemplate)
            .systemParams(systemParams)
            .userText(defaultUserTextTemplate)
            .userParams(userParams)
        chatOptions?.let { builder.chatOptions(it) }
        return chain.nextAroundCall(builder.build())
    }
}