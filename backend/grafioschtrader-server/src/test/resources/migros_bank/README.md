# Migros Bank PDF import fixtures

The 20 text fixtures cover selected statements from 2007 to 2026, including 17 examples extracted from original PDFs
with `ImportTransactionHelperPdf.transFormPDFToTxt` (PDFBox, sorted by position). They retain the extracted line order
and transaction amounts. Names, addresses, personal telephone numbers, customer/account/depot numbers, order references
and document identifiers are replaced with example values. Original PDFs are not included or modified.

New examples have net amounts of at most CHF 9,684.05, USD 4,573.93 or EUR 2,453.34. The largest nominal bond amount
printed in a new example is CHF 35,000. All selected documents stay below the requested CHF 45,000 ceiling.

`expected.csv` records independently checked values from the statements. `MigrosBankPdfTemplateTest` checks the entire
template collection against those values, including execution/payment date, units, price, currencies, fees, taxes,
accrued interest, ex-date and the reconciliation of the booked net amount. Additional cases protect optional interest.

Covered variants:

- Legacy uppercase statements with comma thousands separators, `BERN VALUTA` and Valor references.
- Legacy dividend payments with two-digit years, withholding tax and currency conversion.
- The 2009–2014 layout with apostrophe thousands separators and trailing minus signs.
- The 2015–2021 layout with leading minus signs and changed header placement.
- The 2022–2026 headers, bond purchases with accrued interest, bond coupons and repeated trade executions.
- US dividends with two separate taxes, and distributions without withholding tax.

Trade dates come from the execution sentence, because order dates can differ. Coupon/distribution dates come from
the payment sentence and can differ from the date in the filename or document header. Bond nominal amounts are divided
by 100 through the template's percentage marker, as expected by the import model.

The oldest statements contain a Valor reference but no ISIN. Their templates retain the Valor in `sf1`; these examples
do not prove automatic assignment to a security. Legacy bond purchases and special events such as redemptions,
cancellations, splits and liquidations are outside this fixture set.

Run without Spring or database access from `backend/`:

```text
mvn -pl grafioschtrader-server -Dtest=MigrosBankPdfTemplateTest test
```

The production-database-backed `MigrosBankPdfReaderTest` is not needed for these regression tests.
