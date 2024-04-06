package io.github.alexcheng1982.agentappbuilder.core.planner.nofeedback

import io.github.alexcheng1982.agentappbuilder.core.planner.LLMPlanner
import io.github.alexcheng1982.agentappbuilder.core.planner.LLMPlannerFactory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource

/**
 * Planning without feedback
 */
object NoFeedbackPlannerFactory : LLMPlannerFactory() {
    override fun defaultBuilder(): LLMPlanner.Builder {
        return LLMPlanner.Builder()
            .withUserPromptTemplate(PromptTemplate(ClassPathResource("prompts/no-feedback/user.st")))
            .withSystemPromptTemplate(PromptTemplate(ClassPathResource("prompts/no-feedback/system.st")))
            .withOutputParser(NoFeedbackOutputParser.INSTANCE)
            .withSystemInstruction("You are a helpful assistant.")
    }
}