# Security Policy

## Privacy-First Design

M-Pesa Ledger is built with privacy as a core principle:

- **No internet permission** - Your data never leaves your device
- **No cloud services** - No Google Play Services, Firebase, or analytics
- **No backups** - Data doesn't sync to cloud or transfer to new devices
- **Read-only SMS access** - Never sends, modifies, or deletes messages
- **Local-only storage** - All data stays in your phone's private storage

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in M-Pesa Ledger, please report it responsibly.

### What to Report

Security issues include:
- Ways to access data outside the app sandbox
- Permission bypasses
- SMS content leaks in logs
- Vulnerabilities in biometric authentication
- Any breach of the privacy-first design

### How to Report

**Please do not open public issues for security vulnerabilities.**

Instead, email security reports to: **[Add your email here]**

Include:
- Description of the vulnerability
- Steps to reproduce
- Affected versions
- Your suggested fix (if any)

### What to Expect

- Acknowledgment within 48 hours
- Assessment and response within 7 days
- Fix in the next release for confirmed issues
- Credit in release notes (if you want it)

## Security Best Practices for Users

- Enable app lock (Settings → Security → App lock)
- Review SMS sources (Settings → Sources) - only enable what you use
- Keep your Android version updated
- Download only from trusted sources
- Review permissions before granting

## Security Best Practices for Developers

- Never log SMS content, names, phone numbers, amounts, or codes
- Use `AppLog` which is no-op in release builds
- Keep sensitive data out of screenshots and logs
- Test with demo mode data only
- Don't add `INTERNET` permission or cloud dependencies
- Validate all parsed data before storage
- Use parameterized SQL queries (Room handles this)
- Test biometric authentication edge cases

## Third-Party Dependencies

We minimize dependencies to reduce attack surface. All dependencies are:
- From trusted sources (Google Maven, Maven Central)
- Version-pinned in `gradle/libs.versions.toml`
- Regularly updated for security patches

Run `./gradlew :app:dependencies` to see the full dependency tree.

## Code Signing

Release APKs are signed with a release key that is:
- Generated with RSA 4096-bit encryption
- Stored securely offline
- Never committed to version control
- Protected by strong passwords

Users should verify APK signatures before installation.

## Privacy Audit

The app has no telemetry or analytics. You can verify this by:
1. Reviewing the source code (it's open source)
2. Checking `AndroidManifest.xml` - no `INTERNET` permission
3. Using network monitoring tools (you'll see zero traffic)
4. Inspecting `build.gradle.kts` - no analytics dependencies

## Data Encryption

- Data at rest: Android's built-in app sandbox and file encryption
- In memory: Standard Android memory protection
- No data in transit (no network communication)

Users can enable device encryption through Android settings for additional protection.

## Responsible Disclosure

We believe in responsible disclosure and will:
- Work with you to understand and fix the issue
- Keep you informed throughout the process
- Credit you appropriately (unless you prefer to remain anonymous)
- Not take legal action against good-faith security researchers
