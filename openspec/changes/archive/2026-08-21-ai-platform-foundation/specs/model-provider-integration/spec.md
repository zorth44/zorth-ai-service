## ADDED Requirements

### Requirement: Spring AI model invocation
The system SHALL invoke the configured OpenAI-compatible chat model through an official Spring AI starter, a Spring-managed `ChatClient`, and the provider-neutral `ChatModel` abstraction. Application code MUST NOT implement its own HTTP client for the model API or manually construct a provider-specific model throughout business code.

#### Scenario: Configured model returns content
- **WHEN** a valid chat request is made and the configured OpenAI-compatible provider returns text successfully
- **THEN** `SpringAiChatService` returns that text through the provider-neutral chat response

#### Scenario: Controller remains isolated from Spring AI
- **WHEN** the HTTP controller handles a chat request
- **THEN** its model-facing dependency is only `AiChatService`

### Requirement: Externally configured provider
The system MUST obtain the provider API key, base URL, and model name from Spring configuration backed by environment variables or other external configuration. The repository MUST NOT contain a real API key, password, token, or environment-specific secret.

#### Scenario: Runtime settings are supplied externally
- **WHEN** the server starts with `AI_API_KEY`, `AI_BASE_URL`, and `AI_MODEL` supplied by its runtime environment
- **THEN** the Spring AI model is configured with those values without source-code changes

#### Scenario: Repository is inspected for credentials
- **WHEN** the committed project configuration and documentation are reviewed
- **THEN** they contain only placeholders or non-sensitive defaults and no real provider credential

### Requirement: Provider-neutral platform code
The system SHALL confine concrete OpenAI-compatible provider dependencies and configuration to the server integration boundary. `AiChatService`, its request/response types, and the HTTP controller SHALL NOT contain OpenAI, DeepSeek, Qwen, Claude, Ollama, or other provider-specific types in their public contracts.

#### Scenario: Provider is replaced
- **WHEN** operators replace the concrete `ChatModel` bean and its external configuration with another Spring AI-supported implementation
- **THEN** the controller and `AiChatService` API remain unchanged

### Requirement: Model-service-independent automated tests
The default automated test suite SHALL run without access to a model service, a real model call, or a real provider API key.

#### Scenario: Maven tests run in an isolated environment
- **WHEN** `mvn clean test` is executed without provider credentials and without network access to a model endpoint
- **THEN** unit, controller, and application-context tests complete using mocks or test-only configuration and make no external model request

#### Scenario: Real-provider verification is opt-in
- **WHEN** a developer runs the default Maven test lifecycle
- **THEN** no real-provider integration test is required or invoked automatically
