package io.github.alexcheng1982.agentappbuilder.example.universities;

import io.github.alexcheng1982.agentappbuilder.core.Planner;
import io.github.alexcheng1982.agentappbuilder.core.planner.simple.SimplePlannerFactory;
import io.github.alexcheng1982.agentappbuilder.core.tool.AgentToolsProvider;
import io.github.alexcheng1982.agentappbuilder.spring.AgentToolFunctionCallbackContext;
import io.github.alexcheng1982.agentappbuilder.spring.agentcontroller.AgentControllerConfiguration;
import io.github.alexcheng1982.agentappbuilder.spring.dev.AgentDevConfiguration;
import io.github.alexcheng1982.agentbuilder.tool.universities.client.api.UniversitiesApi;
import io.github.alexcheng1982.agentbuilder.tool.universities.client.api.UniversitiesApiAgentToolConfiguration;
import io.github.alexcheng1982.springai.dashscope.DashscopeChatClient;
import io.github.alexcheng1982.springai.dashscope.DashscopeChatOptions;
import io.github.alexcheng1982.springai.dashscope.api.DashscopeApi;
import io.github.alexcheng1982.springai.dashscope.api.DashscopeModelName;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration
@Import({AgentDevConfiguration.class,
    AgentControllerConfiguration.class,
    UniversitiesApiAgentToolConfiguration.class})
public class AppConfiguration {

  @Bean
  @Primary
  public ChatClient chatClient(
      FunctionCallbackContext functionCallbackContext) {
    return new DashscopeChatClient(
        new DashscopeApi(),
        DashscopeChatOptions.builder()
            .withModel(DashscopeModelName.QWEN_MAX)
            .withTemperature(0.2f)
            .build(),
        functionCallbackContext
    );
  }

  @Bean
  public FunctionCallbackContext springAiFunctionManager(
      AgentToolsProvider agentToolsProvider,
      ObservationRegistry observationRegistry,
      ApplicationContext context) {
    var manager = new AgentToolFunctionCallbackContext(agentToolsProvider,
        observationRegistry);
    manager.setApplicationContext(context);
    return manager;
  }

  @Bean
  public Planner agentPlanner(ChatClient chatClient,
      AgentToolsProvider agentToolsProvider,
      ObservationRegistry observationRegistry) {
    return SimplePlannerFactory.INSTANCE.create(
        chatClient,
        agentToolsProvider,
        null,
        null,
        observationRegistry,
        null);
  }

  @Bean
  public UniversitiesApi universitiesApi() {
    return new UniversitiesApi();
  }

}
