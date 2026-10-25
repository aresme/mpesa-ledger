# Changelog

All notable changes to M-Pesa Ledger will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-15

### Added
- Initial release
- M-Pesa SMS parser with support for 15+ transaction types
- Dashboard with monthly financial overview
- Category-based spending tracking with editable rules
- Budget management with notifications at 80% and 100%
- Automatic bill detection and reminders
- Fuliza loan tracking
- Support for multiple financial SMS sources (GlobalPay, Safaricom, KCB, Okoa Jahazi, Ziidi, e-top-up)
- Biometric app lock (fingerprint, face, or device credential)
- Export to CSV and JSON
- Import from text files and termux-sms-list JSON
- Demo mode for testing and screenshots
- 12-month income vs spending trend chart
- Top recipients tracking
- Dark theme with Material 3 design

### Security
- Completely offline (no internet permission)
- No Google Play Services or analytics
- SMS read-only access
- Local-only data storage
- Biometric authentication support
- No cloud backups

### Technical
- Built with Kotlin, Jetpack Compose, Room database
- Minimum Android 7.0 (API 24)
- Target Android 14 (API 37)
- Manual dependency injection
- Comprehensive unit and instrumented tests

---

## Unreleased

_Future changes will be documented here_

---

### Change Types
- **Added** - New features
- **Changed** - Changes to existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Security** - Security improvements
