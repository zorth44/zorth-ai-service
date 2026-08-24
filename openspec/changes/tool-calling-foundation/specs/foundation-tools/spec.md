## ADDED Requirements

### Requirement: Current-date tool
The system SHALL register a `getCurrentDate` tool whose description states that it returns the current server date and is appropriate for questions about today/current date or tasks requiring current-date knowledge. The tool SHALL obtain time from an application-provided clock and return a structured result containing the `LocalDate` and day of week.

#### Scenario: Current date is returned as structured data
- **WHEN** `getCurrentDate` executes with a configured clock
- **THEN** it returns a date equal to that clock's current date and the matching day-of-week value

### Requirement: Date-difference tool
The system SHALL register a `calculateDaysBetween` tool whose description states that it is used only when both start and end dates are known. It SHALL accept explicit start and end dates and return the signed number of days from start to end, with positive, zero, and negative values representing later, equal, and earlier end dates respectively.

#### Scenario: End date is after start date
- **WHEN** `calculateDaysBetween` receives a start date earlier than the end date
- **THEN** it returns the positive number of calendar days from start to end

#### Scenario: Dates are equal
- **WHEN** `calculateDaysBetween` receives equal start and end dates
- **THEN** it returns zero

#### Scenario: End date is before start date
- **WHEN** `calculateDaysBetween` receives a start date later than the end date
- **THEN** it returns the corresponding negative number of calendar days

### Requirement: Calculator tool
The system SHALL register a `calculate` tool for deterministic addition, subtraction, multiplication, and division. Its model-controlled input SHALL contain required `BigDecimal left`, `BigDecimal right`, and `Operation operation` fields, and its structured result SHALL contain a `BigDecimal result`. Division SHALL use a documented finite precision policy.

#### Scenario: Supported arithmetic succeeds
- **WHEN** `calculate` receives valid operands and any of `ADD`, `SUBTRACT`, `MULTIPLY`, or `DIVIDE` with a non-zero divisor
- **THEN** it returns the mathematically corresponding structured result using the configured decimal precision policy

#### Scenario: Division by zero fails safely
- **WHEN** `calculate` receives `DIVIDE` with a zero right operand
- **THEN** tool execution fails through the common tool exception path and does not return a fabricated numeric result

#### Scenario: Invalid calculator input fails safely
- **WHEN** `calculate` receives a missing required operand or operation
- **THEN** tool execution fails through the common tool exception path

### Requirement: Configuration-backed system-information tool
The system SHALL register a `getSystemInfo` tool whose description states that it returns information about the current AI Platform service and is appropriate for questions about that service. It SHALL return structured application name, environment, and version fields populated from Spring configuration rather than hard-coded runtime values.

#### Scenario: Configured system information is returned
- **WHEN** application name, environment, and version are supplied through Spring configuration and `getSystemInfo` executes
- **THEN** the returned `SystemInfo` fields equal the configured values

### Requirement: Model-selectable tool metadata
Every registered foundation tool MUST use Spring AI's `@Tool` mechanism, and each description MUST state both what the tool does and when it should be selected. Every model-controlled parameter MUST have a clear name, explicit type, and required/optional status; tools MUST NOT ask the model to guess application-controlled identifiers or configuration.

#### Scenario: Tool schemas expose only model-controlled inputs
- **WHEN** Spring AI creates the JSON schemas for the registered foundation tools
- **THEN** the schemas contain the typed operation inputs needed for the tool and omit request ID, user ID, tenant ID, datasource ID, permissions, and execution ID

#### Scenario: Descriptions distinguish tool purposes
- **WHEN** the model receives the available tool definitions
- **THEN** it can distinguish current-date retrieval, known-date difference calculation, arithmetic calculation, and current-service information by their descriptions

### Requirement: Centralized Spring tool registration
Date, calculator, and system-information tools SHALL be Spring-managed instances registered for the agent in one configuration location. Controllers MUST NOT instantiate tools, and tools MUST NOT depend on `ChatClient`, invoke an LLM, or register database/SQL capabilities.

#### Scenario: Agent receives the fixed foundation tool set
- **WHEN** the application context creates the agent service
- **THEN** the service receives the configured date, calculator, and system-information tool instances without controller construction or dynamic discovery

### Requirement: Structured tool results
At least the current-date, calculator, and system-information tools SHALL return Java records or POJOs rather than unstructured strings so Spring AI can serialize their fields into tool-result messages.

#### Scenario: Structured result reaches the model
- **WHEN** a registered structured-result tool completes successfully
- **THEN** Spring AI serializes the result fields for the model to use in its next response

