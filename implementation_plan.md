# Implementation Plan - Digital Expense Tracker

Building an Android application to track digital expenses and investments by reading incoming SMS messages.

## User Review Required

- **Permissions**: The app requires `READ_SMS` and `RECEIVE_SMS` permissions. Since it's for personal use, these are requested at runtime.
- **Privacy**: All data remains local on the device in a Room (SQLite) database.
- **Parsing**: Regex patterns are tuned for common Indian bank SMS formats.

## Proposed Changes

### Project Configuration
- **Dependencies**: Added Room (Database), Jetpack Compose (UI), and Navigation Compose.
- **Build System**: Enabled Compose and configured KSP (later switched to annotation processor for stability).

### Data Layer (Room)
- **Transaction Entity**: Stores raw SMS, amount, type (DEBIT/CREDIT/REFUND/TRANSFER), merchant, timestamp, and category.
- **MerchantMapping Entity**: Stores learned rules for auto-categorization.
- **TransactionDao**: Queries for dashboard totals, recent transactions, and rule-based category lookups.
- **AppDatabase**: Singleton database provider.

### Logic & Processing
- **SmsParser**: Multi-pass regex engine to extract data and clean merchant names.
- **SmsReceiver**: BroadcastReceiver that triggers `SmsParser` and saves results to Room.

### UI Layer (Jetpack Compose)
- **Dashboard**: Monthly spend overview with category charts and transaction timeline.
- **Review Screen**: Triage for "Uncategorized" items with one-tap learning.
- **Rules Screen**: Management of learned merchant-to-category rules.

## Verification Plan

### Automated Tests
- `SmsParserTest.kt`: 9 unit tests verifying amount extraction, type detection (including refunds/transfers), and merchant cleaning.
- Command: `./gradlew app:testDebugUnitTest`

### Manual Verification
- Deploy to device/emulator.
- Grant SMS permissions.
- Test real-time ingestion by sending mock SMS via ADB.
- Verify auto-learning by categorizing an item and checking if future items auto-assign.
