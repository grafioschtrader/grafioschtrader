# PostFinance / E-Trading PDF import fixtures

The collection contains 21 transaction statements and one migration-transfer notice from 2006 to 2026. Nineteen examples
were extracted from original PDFs with `ImportTransactionHelperPdf.transFormPDFToTxt` (PDFBox, sorted by position).
Names, addresses, customer/account numbers and personal document references are anonymized; line order and financial
values are retained. Original PDFs are neither included nor modified.

The added examples have net amounts no larger than CHF 15,061.15, USD 3,439.78 or EUR 55.88. Their largest printed bond
nominal amount is CHF 15,000. All examples stay below the requested CHF 45,000 ceiling.

The archive contains `Titeltransfer: Eingang` notices dated 15 May 2016. They mark the platform migration and are not
new purchases. The negative fixture protects against importing the original purchase a second time. The existing new
platform template validity dates (16 May 2016) are retained. Transfers are not supported by these transaction templates.

Covered cases include:

- Old Yellowtrade share/ETF trades, including repeated executions, with dot thousands separators and decimal commas.
- Pre-migration bond purchases with percentage prices and optional accrued interest, including the reported Firstrand
  purchase dated 13 June 2014.
- Old distributions and bond coupons with dot or space thousands separators and decimal commas.
- Post-migration share/ETF trades, bond trades with or without accrued interest, repayments, coupons and distributions.
- Trading Credit discounts on both bond and share sales.
- Different statement, execution and value dates: execution dates are used where explicitly provided.

`expected.csv` stores independently checked values for the 21 transaction statements. The old CHF distribution dated
29 April 2016 prints 650 units at CHF 2.1912 and a gross/net amount of CHF 1,424.30. Their arithmetic difference is
CHF 0.02. Its legacy distribution template configures `calcRounding=CHF=0.05`; the test retains and checks the actual
difference and the configured step. This tolerance is limited to old CHF distributions. A separate test proves that a
missing CHF 2 execution fee on the Firstrand purchase cannot be accepted as rounding.

Only the reviewed template variants were changed. The other PDF templates are included to test the actual template
selection together with them; this collection does not establish coverage of every archive document, foreign-currency
conversion, old cancellation or corporate-action variant. The CSV template is outside this test's scope.

Run from `backend/`, without Spring or database access:

```text
mvn -pl grafioschtrader-server -Dtest=PostfinancePdfTemplateTest test
```
