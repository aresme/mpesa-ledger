# Contributing to M-Pesa Ledger

Thanks for your interest in contributing! This document outlines how to contribute to the project.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/mpesa-ledger.git`
3. Create a feature branch: `git checkout -b feature/your-feature-name`
4. Make your changes
5. Test thoroughly
6. Commit with a clear message
7. Push and create a pull request

## Development Setup

See the main README for build requirements. Quick start:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

scripts/install.sh      # Build and install debug version
scripts/logs.sh         # View logs
./gradlew :app:testDebugUnitTest    # Run tests
```

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and small
- Write tests for new features

## Parser Contributions

When adding support for new SMS formats:

1. Get a masked example (no real names, numbers, or codes)
2. Add the pattern to `parser/MpesaParser.kt` at the correct priority
3. Add a `Kind` if needed in `parser/Model.kt`
4. Write a test in `app/src/test/.../parser/EdgeCaseTest.kt`
5. Bump `MpesaParser.VERSION`

Never include real SMS content, names, phone numbers, or transaction codes in commits or issues.

## Testing

- **Unit tests**: `./gradlew :app:testDebugUnitTest`
- **Instrumented tests**: `scripts/androidtest.sh`
- Test on actual devices when possible
- Use demo mode for screenshots and testing

## Commit Messages

Use conventional commits format:

- `feat: add bill reminder snooze option`
- `fix: correct date parsing for M-Shwari messages`
- `docs: update installation instructions`
- `test: add edge case for Fuliza repayment`
- `refactor: simplify category matching logic`

## Pull Request Guidelines

- Describe what your PR does and why
- Reference any related issues
- Include screenshots for UI changes (use demo mode!)
- Ensure all tests pass
- Keep PRs focused on a single feature or fix
- Update documentation if needed

## Privacy Rules

This is a privacy-first app. Follow these rules:

- Never log SMS content, names, amounts, or balances
- Use `AppLog` for logging (no-op in release builds)
- Screenshots must use demo mode only
- Don't request `INTERNET` permission
- Keep data local - no cloud services

## Questions?

Open an issue for:
- Bug reports
- Feature requests
- Questions about the codebase
- SMS format you can't parse (provide masked example)

## Code of Conduct

- Be respectful and constructive
- Focus on the code, not the person
- Help others learn
- No harassment or discrimination

## License

By contributing, you agree that your contributions will be licensed under the same terms as the project (see LICENSE file).
