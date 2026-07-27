# FIN-Trace - Walkthrough

I have completed the development and optimization of **FIN-Trace**, your intelligent, privacy-focused Android application for tracking digital expenses and investments.

## Key Accomplishments

### 1. Robust SMS Parsing & Investment Tracking
- **Smart Extraction**: Detects amounts, transaction types (Debit/Credit/Refund/Transfer/Investment), and merchant names.
- **Investment Focus**: Specifically identifies keywords like "Mutual Fund", "SIP", and "Stock" to separate long-term investments from daily expenses.
- **Merchant Cleaning**: Strips junk like UPI suffixes (@okaxis) and terminal IDs to keep the dashboard professional.

### 2. Historical Data Scan
- **Instant Population**: Automatically scans your existing SMS inbox on the first launch (after permission) to build your financial history instantly.
- **Intelligent Triage**: Uncategorized historical items appear in the "Review" tab for quick categorization.

### 3. Multi-Timeframe Dashboard
- **Dynamic Views**: Toggle between **Daily, Weekly, Monthly, and Yearly** summaries.
- **Dual Tracking**: Dedicated summary cards for **Expenses** (Net Spent) and **Investments**.
- **Live Updates**: Dashboard updates instantly as new SMS messages arrive.

### 4. Performance & Efficiency
- **Zero Heat**: Database listeners only run when the app is in use, preventing background drain and device overheating.
- **On-Device Only**: Your financial data never leaves your device.

## Verification Summary

### Automated Tests
- **SmsParserTest**: 10 comprehensive unit tests passed, covering:
    - Investments (SIP, Mutual Funds)
    - UPI Debits & Credits
    - Refunds & Reversals
    - Self-Transfers
    - Merchant name cleaning & Title casing
- Command: `./gradlew app:testDebugUnitTest`

### Manual Verification Steps
1. **First Launch**: Open **FIN-Trace** and grant SMS permissions.
2. **Auto-Scan**: Observe your historical data populating the dashboard and "Recent Activity".
3. **Timeframe Toggle**: Use the tabs (Daily/Weekly/etc.) to see how your spending habits change over time.
4. **Categorize**: Use the "Review" tab to teach the app about your recurring merchants.

The application is now a comprehensive financial tool, tailored for your personal use. Enjoy tracking your journey with **FIN-Trace**!
