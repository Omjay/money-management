# Hisaab product direction

This note records public product patterns, not personal finance data. It does not imply that Hisaab has implemented the features below.

## What comparable apps make useful

- [Wallet](https://budgetbakers.com/en/products/wallet/features/) has category budgets and spending insights. Its [planned-payments view](https://budgetbakers.com/en/products/wallet/features/planned-payments/) projects upcoming obligations.
- [YNAB](https://www.ynab.com/features) emphasizes goals and spending/net-worth reports. Its [reconciliation guide](https://support.ynab.com/en_us/reconciling-accounts-a-guide-BJFE3fHys) treats matching the ledger to the bank as a recurring habit, not a hidden calculation.
- [Monarch](https://www.monarch.com/features/tracking) makes transactions searchable and reviewable, supports category/time drill-downs, and shows recurring obligations alongside assets and liabilities.
- [Money Manager](https://www.realbyteapps.com/) combines category budgets with calendar views and asset graphs.

## Order for Hisaab

1. **Trust the import first.** Show statement coverage, unparsed rows, gaps between statement periods, duplicates, and whether an account balance reconciles. Never present an inferred balance as live.
2. **Make every transaction explainable.** Distinguish purchases, income, transfers, card settlements, refunds, investments, and loan movements. Let a user correct uncertain rows and reverse rules.
3. **Make the dashboard actionable.** Only after the ledger is trustworthy, add category budgets, upcoming obligations, and goals. Keep historical filters and drill-downs tied to source evidence.
4. **Keep the privacy boundary.** These ideas do not justify silent bank, SMS, email, or network access. Any future integration requires separate opt-in and review.

Hisaab currently implements parts of steps 1 and 2. The rest is a roadmap, not a release claim.
