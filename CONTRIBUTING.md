# Contributing to Autonomous SWE Agent

Thank you for your interest in contributing to the Autonomous SWE Agent Platform!

## Development Guidelines

1. **Java 21 Code Standard**:
   - Follow standard Java code conventions.
   - Maintain Single Responsibility Principle (SRP) across service classes.
   - Use Lombok annotations (`@RequiredArgsConstructor`, `@Getter`, `@Setter`, `@Slf4j`) where appropriate.

2. **Commit Message Format**:
   - Use conventional commit style prefixes:
     - `feat:` New feature implementation
     - `fix:` Bug fix or rate-limit resolution
     - `refactor:` Code refactoring without behavioral change
     - `test:` Unit or integration test additions
     - `docs:` Documentation updates

3. **Pull Request Checklist**:
   - [ ] All new code is covered by unit tests in `src/test/java`.
   - [ ] Project compiles cleanly via `mvn clean install`.
   - [ ] Unit test suite passes cleanly via `mvn test`.
   - [ ] No hardcoded API keys or secrets are introduced.
