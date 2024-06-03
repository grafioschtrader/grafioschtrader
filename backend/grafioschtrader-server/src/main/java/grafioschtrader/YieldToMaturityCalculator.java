package grafioschtrader;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;

public class YieldToMaturityCalculator {

    // Input parameters
    private double Price; // Bond price
    private double FaceValue; // Bond face value
    private double CouponRate; // Bond coupon rate
    private LocalDate SettlementDate; // Bond settlement date
    private LocalDate MaturityDate; // Bond maturity date
    private double frequency; // Coupon payment frequency per year
    private double daysInYear; // Number of days in a year according to the day count convention

    // Constructor
    public YieldToMaturityCalculator(double Price, double FaceValue, double CouponRate, LocalDate SettlementDate, LocalDate MaturityDate, double frequency, double daysInYear) {
        this.Price = Price;
        this.FaceValue = FaceValue;
        this.CouponRate = CouponRate;
        this.SettlementDate = SettlementDate;
        this.MaturityDate = MaturityDate;
        this.frequency = frequency;
        this.daysInYear = daysInYear;
    }

    // Method to calculate YTM
    public double calculateYTM() {
        // Define the function to be solved
        UnivariateFunction function = new UnivariateFunction() {
            @Override
            public double value(double r) {
                double sum = 0.0;
                double coupon = CouponRate * FaceValue / frequency; // Coupon payment
                double dsc = ChronoUnit.DAYS.between(SettlementDate, MaturityDate); // Number of days from settlement to maturity
                double e = Math.floor(dsc / (daysInYear / frequency)); // Number of full coupon periods from settlement to maturity
                double d1 = ChronoUnit.DAYS.between(SettlementDate, MaturityDate.minusDays((long) (e * daysInYear / frequency))); // Number of days from beginning of coupon period to settlement
                double d2 = ChronoUnit.DAYS.between(SettlementDate.minusDays((long) d1), SettlementDate); // Number of days from settlement to end of coupon period
                double accruedInterest = coupon * d1 / (d1 + d2); // Accrued interest
                double c = (Price + accruedInterest) / FaceValue; // Clean price
                for (double j = 1; j <= e; j++) {
                    sum += coupon / Math.pow(1 + r / frequency, j + d1 / (d1 + d2));
                }
                sum += FaceValue / Math.pow(1 + r / frequency, e + d1 / (d1 + d2));
                return c - sum / FaceValue;
            }
        };

        // Create a solver with default accuracy and iterations
        BrentSolver solver = new BrentSolver();

        // Solve the function in the range [0, 1]
        double ytm = solver.solve(100, function, 0, 1);

        // Return the annualized YTM
        return ytm * frequency;
    }




    // Main method for testing
    public static void main(String[] args) {
        // Create a bond object with sample data
//      YieldToMaturityCalculator bond = new YieldToMaturityCalculator(92.5, 100, 0.0166 / 100, LocalDate.of(2024, 1, 29),
//          LocalDate.of(2029, 12, 21), 1, 360);

      YieldToMaturityCalculator bond = new YieldToMaturityCalculator(96.2, 100, 0.0732 / 100, LocalDate.of(2024, 1, 29),
          LocalDate.of(2025, 1, 30), 1, 360);

      // Calculate and print the YTM
      double ytm = bond.calculateYTM();
      System.out.println("The yield to maturity is " + ytm * 100);
    }
}
