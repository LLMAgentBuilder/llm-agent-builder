package io.github.llmagentbuilder.cli

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import io.github.llmagentbuilder.core.AgentConfig
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.FileReader
import java.nio.file.Path
import java.util.*

object MavenFilesGenerator {
    private val handlebars = Handlebars(ClassPathTemplateLoader("/template"))

    fun generatePom(
        config: GenerationConfig,
        agentConfig: AgentConfig
    ): String {
        val template = handlebars.compile("pom.xml")
        val pom = template.apply(
            mapOf(
                "groupId" to (config.groupId
                    ?: "io.github.llmagentbuilder.app"),
                "artifactId" to (config.artifactId
                    ?: "app_${UUID.randomUUID().toString().replace("-", "")}"),
                "springAiVersion" to (config.springAiVersion
                    ?: "1.0.0-SNAPSHOT"),
                "llmAgentBuilderVersion" to (config.llmAgentBuilderVersion
                    ?: "0.2.0"),
                "dependencies" to collectDependencies(agentConfig),

                )
        )
        return pom

    }

    private fun collectDependencies(agentConfig: AgentConfig): List<MavenCoordinate> {
        val plannerDeps =
            collectDependencies(agentConfig.planner, plannerDependencies)
        val llmDeps = collectDependencies(agentConfig.llm, llmDependencies)
        val toolDeps = agentConfig.tools?.mapNotNull {
            it.dependency?.let { dep ->
                MavenCoordinate(
                    dep.groupId,
                    dep.artifactId,
                    dep.version
                )
            }
        }?.toList() ?: listOf()
        return plannerDeps + llmDeps + toolDeps
    }

    private fun collectDependencies(
        subConfig: Map<String, Any?>?,
        allDeps: Map<String, MavenCoordinate>
    ): List<MavenCoordinate> {
        val deps = mutableListOf<MavenCoordinate>()
        subConfig?.let {
            it.entries.forEach { entry ->
                if ((entry.value as? Map<*, *>)?.get("enabled") == true) {
                    allDeps[entry.key]?.let { dep -> deps.add(dep) }
                }
            }
        }
        return deps.toList()
    }

    fun generateAssemblyDescriptor(): String {
        val template = handlebars.compile("agent-jar-assembly.xml")
        return template.apply(mapOf<String, Any?>())
    }
}

fun main(args: Array<String>) {
    val configFile = Path.of(args.first())
    val config =
        Yaml(
            Constructor(
                AgentConfig::class.java,
                LoaderOptions()
            )
        ).load<AgentConfig>(
            FileReader(
                configFile.toFile()
            )
        )
    MavenFilesGenerator.generatePom(
        GenerationConfig(), config
    )
}