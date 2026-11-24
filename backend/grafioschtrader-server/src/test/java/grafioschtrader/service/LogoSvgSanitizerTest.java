package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.service.LogoSvgSanitizer.SanitizedSvg;

class LogoSvgSanitizerTest {

  private static final String VALID_SVG = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 40">
        <!-- stripped comment -->
        <defs>
          <linearGradient id="g1" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stop-color="#0055aa"/>
            <stop offset="100%" stop-color="#00aaff"/>
          </linearGradient>
        </defs>
        <g>
          <rect x="0" y="0" width="120" height="40" fill="url(#g1)"/>
          <path d="M10 20 L110 20" stroke="#ffffff"/>
          <use href="#g1"/>
        </g>
      </svg>
      """;

  @Test
  void acceptsValidSvgStripsCommentsAndComputesSha256() {
    SanitizedSvg result = LogoSvgSanitizer.sanitize(VALID_SVG);
    assertThat(result.svgContent()).contains("<svg").contains("viewBox=\"0 0 120 40\"")
        .doesNotContain("stripped comment").doesNotContain("<?xml");
    assertThat(result.contentSha256()).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(result.contentSha256()).isEqualTo(LogoSvgSanitizer.sha256Hex(result.svgContent()));
  }

  @Test
  void derivesViewBoxFromPositiveNumericWidthAndHeight() {
    String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"80px\" height=\"30\"><rect width=\"80\" height=\"30\"/></svg>";
    SanitizedSvg result = LogoSvgSanitizer.sanitize(svg);
    assertThat(result.svgContent()).contains("viewBox=\"0 0 80 30\"");
  }

  @Test
  void rejectsMissingViewBoxWithoutNumericDimensions() {
    String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\"/></svg>";
    assertInvalid(svg);
  }

  @Test
  void rejectsNegativeOrZeroViewBoxDimensions() {
    String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 0 40\"><rect width=\"10\" height=\"10\"/></svg>";
    assertInvalid(svg);
  }

  @Test
  void rejectsNullOrBlankSvg() {
    assertInvalid(null);
    assertInvalid("   ");
  }

  @Test
  void rejectsOversizedSvgOver32Kb() {
    String padding = "a".repeat(LogoSvgSanitizer.MAX_BYTES);
    String oversized = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><desc>" + padding
        + "</desc></svg>";
    assertThatThrownBy(() -> LogoSvgSanitizer.sanitize(oversized)).isInstanceOf(DataViolationException.class)
        .satisfies(e -> assertThat(((DataViolationException) e).getDataViolation().getFirst().getMessageKey())
            .isEqualTo("gt.logo.file.too.large"));
  }

  @Test
  void rejectsScriptStyleFilterForeignObjectAndImage() {
    assertInvalid("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><script>alert(1)</script></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><style>.a{fill:red}</style><rect/></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><defs><filter id=\"f\"/></defs></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><foreignObject><div>x</div></foreignObject></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><image href=\"https://example.com/a.png\"/></svg>");
  }

  @Test
  void rejectsDoctypeAndEventHandlers() {
    assertInvalid(
        "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>");
    assertInvalid("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\" onload=\"alert(1)\"/>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><rect onclick=\"x()\" width=\"5\" height=\"5\"/></svg>");
  }

  @Test
  void rejectsExternalHrefAndExternalUrlReferences() {
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><use href=\"https://evil.example/x.svg#a\"/></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><rect href=\"#local\" width=\"5\" height=\"5\"/></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><rect fill=\"url(https://evil.example/g#a)\" width=\"5\" height=\"5\"/></svg>");
    assertInvalid(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><rect style=\"background:javascript:alert(1)\" width=\"5\" height=\"5\"/></svg>");
  }

  @Test
  void rejectsTooManyElements() {
    StringBuilder sb = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">");
    for (int i = 0; i < LogoSvgSanitizer.MAX_ELEMENTS; i++) {
      sb.append("<g/>");
    }
    sb.append("</svg>");
    assertInvalid(sb.toString());
  }

  private static void assertInvalid(String svg) {
    assertThatThrownBy(() -> LogoSvgSanitizer.sanitize(svg)).isInstanceOf(DataViolationException.class)
        .satisfies(e -> assertThat(((DataViolationException) e).getDataViolation().getFirst().getMessageKey())
            .isEqualTo("gt.logo.svg.invalid"));
  }
}
