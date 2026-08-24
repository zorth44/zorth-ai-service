# Service Operations

## Purpose

Define health reporting, sanitized server failures, and non-sensitive model-call logging for operating the AI service safely.

## Requirements

### Requirement: Application health endpoint
The system SHALL expose Spring Boot Actuator health information at `/actuator/health`, and evaluating that endpoint MUST NOT invoke a chat model or incur a provider request.

#### Scenario: Healthy server reports UP
- **WHEN** the initialized server receives `GET /actuator/health`
- **THEN** it responds successfully with health status `UP`

#### Scenario: Health check does not call the model
- **WHEN** an operations platform repeatedly polls `/actuator/health`
- **THEN** no `ChatClient` or `ChatModel` call is made as a consequence of the polling

### Requirement: Sanitized AI service failures
The system MUST translate model or platform AI failures into HTTP 500 with error code `AI_SERVICE_ERROR`, and MUST translate otherwise unhandled failures into HTTP 500 with error code `INTERNAL_ERROR`. Client responses MUST NOT contain provider SDK details, stack traces, credentials, authorization values, or full sensitive configuration.

#### Scenario: Model invocation fails
- **WHEN** the configured model client throws an error during a chat request
- **THEN** the client receives HTTP 500 with code `AI_SERVICE_ERROR` and a safe message while the detailed exception is retained in server logs

#### Scenario: Unexpected server failure occurs
- **WHEN** an unhandled non-AI exception reaches the global exception handler
- **THEN** the client receives HTTP 500 with code `INTERNAL_ERROR` and no stack trace or internal exception detail

### Requirement: Non-sensitive model-call logging
The system SHALL log chat request start, success, and failure outcomes together with model-call duration and message length. It MUST NOT log request content, response content, API keys, authorization headers, or full sensitive provider configuration by default.

#### Scenario: Successful model call is logged
- **WHEN** a model-backed chat request succeeds
- **THEN** server logs include a success outcome, elapsed duration, and message length without prompt or response content

#### Scenario: Failed model call is logged safely
- **WHEN** a model-backed chat request fails
- **THEN** server logs include a failure outcome and diagnostic exception without emitting credentials, authorization headers, or prompt content
