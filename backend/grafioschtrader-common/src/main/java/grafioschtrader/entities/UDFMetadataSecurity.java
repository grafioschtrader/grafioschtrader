package grafioschtrader.entities;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import grafiosch.common.EnumHelper;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.UDFMetadata;
import grafiosch.types.UDFDataType;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.UDFFillPeriod;
import grafioschtrader.types.UDFFillSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

@Schema(description = """
    For securities, different fields can be defined for different instruments and asset classes.
    For example, different information may be relevant to the user for a bond or stock.""")
@Entity
@Table(name = UDFMetadataSecurity.TABNAME)
public class UDFMetadataSecurity extends UDFMetadata {

  public static final String TABNAME = "udf_metadata_security";

  @Schema(description = "An instrument belongs to a group of asset classes, such as shares, bonds, etc.")
  @Column(name = "category_types")
  @PropertyAlwaysUpdatable
  @JsonIgnore
  private long categoryTypes;

  @Schema(description = "An instrument belongs to a group of financial instruments, e.g. ETF, direct, etc.")
  @Column(name = "spec_invest_instruments")
  @PropertyAlwaysUpdatable
  @JsonIgnore
  private long specialInvestmentInstruments;

  @Schema(description = """
      Where the value of this field comes from. MANUAL lets the user type it; every other source computes it and
      records the provenance of each computed cell. Only fields of a user other than 0 without special type can have
      a source other than MANUAL.""")
  @Column(name = "fill_source")
  @PropertyAlwaysUpdatable
  private byte fillSource = UDFFillSource.MANUAL.getValue();

  @Schema(description = """
      The rule of the fill source. For TEMPLATE one or more lines, each an optional condition in brackets followed by a
      text with placeholders from the master data of the security. Null for MANUAL.""")
  @Column(name = "fill_rule")
  @PropertyAlwaysUpdatable
  @Size(max = 1000)
  private String fillRule;

  @Schema(description = """
      How often the instance fills the field by itself, for the fill sources RESOLVER and LLM only; null otherwise.
      Null for LLM means a run starts only on request of the user, null for RESOLVER follows the global setting
      gt.udf.fill.resolver.refresh.days. A refreshed language model value replaces an automatic value without a new
      acceptance.""")
  @Column(name = "fill_period")
  @PropertyAlwaysUpdatable
  private Byte fillPeriod;

  @Schema(description = """
      Incremented by the server whenever the fill source or the fill rule changes. A fill row of an older version was
      produced by an earlier rule and is due again. Never taken from the client.""")
  @Column(name = "fill_rule_version")
  @JsonProperty(access = Access.READ_ONLY)
  private int fillRuleVersion;

  @Schema(description = """
      Whether the field is processed: always for MANUAL; for TEMPLATE and RESOLVER when the rule can be evaluated; for
      LLM only when the home tenant of the owner has a language model. An inactive field keeps its values and stays
      visible; only nothing fills it. Set when the fields are listed for editing.""")
  @Transient
  @JsonProperty(access = Access.READ_ONLY)
  private Boolean active;

  public UDFMetadataSecurity() {
  }

  @JsonCreator(mode = JsonCreator.Mode.DISABLED)
  public UDFMetadataSecurity(long categoryTypes, long specialInvestmentInstruments, Integer idUser, Byte udfSpecialType,
      String description, String descriptionHelp, UDFDataType udfDataType, String fieldSize, byte uiOrder) {
    super(idUser, udfSpecialType, description, descriptionHelp, udfDataType.getValue(), fieldSize, uiOrder);
    this.categoryTypes = categoryTypes;
    this.specialInvestmentInstruments = specialInvestmentInstruments;
  }

  public long getCategoryTypes() {
    return categoryTypes;
  }

  public void setCategoryTypes(long categoryTypes) {
    this.categoryTypes = categoryTypes;
  }

  public long getSpecialInvestmentInstruments() {
    return specialInvestmentInstruments;
  }

  public void setSpecialInvestmentInstruments(long specialInvestmentInstruments) {
    this.specialInvestmentInstruments = specialInvestmentInstruments;
  }

  public void setSpecialInvestmentInstrumentEnums(
      EnumSet<SpecialInvestmentInstruments> specialInvestmentInstrumentEnums) {
    this.specialInvestmentInstruments = EnumHelper.encodeEnumSet(specialInvestmentInstrumentEnums);
  }

  public EnumSet<SpecialInvestmentInstruments> getSpecialInvestmentInstrumentEnums() {
    return EnumHelper.decodeEnumSet(SpecialInvestmentInstruments.class, specialInvestmentInstruments);
  }

  public void setCategoryTypeEnums(EnumSet<AssetclassType> categoryTypeEnums) {
    this.categoryTypes = EnumHelper.encodeEnumSet(categoryTypeEnums);
  }

  public EnumSet<AssetclassType> getCategoryTypeEnums() {
    return EnumHelper.decodeEnumSet(AssetclassType.class, categoryTypes);
  }

  public UDFFillSource getFillSource() {
    return UDFFillSource.getByValue(fillSource);
  }

  public void setFillSource(UDFFillSource fillSource) {
    this.fillSource = fillSource == null ? UDFFillSource.MANUAL.getValue() : fillSource.getValue();
  }

  public String getFillRule() {
    return fillRule;
  }

  public void setFillRule(String fillRule) {
    this.fillRule = fillRule;
  }

  public UDFFillPeriod getFillPeriod() {
    return fillPeriod == null ? null : UDFFillPeriod.getByValue(fillPeriod);
  }

  public void setFillPeriod(UDFFillPeriod fillPeriod) {
    this.fillPeriod = fillPeriod == null ? null : fillPeriod.getValue();
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public int getFillRuleVersion() {
    return fillRuleVersion;
  }

  public void setFillRuleVersion(int fillRuleVersion) {
    this.fillRuleVersion = fillRuleVersion;
  }

  /**
   * Tells whether the value of this field is computed by a fill source instead of typed by the user.
   *
   * @return true for every fill source other than {@link UDFFillSource#MANUAL}
   */
  @JsonIgnore
  public boolean isAutomaticallyFilled() {
    return getFillSource() != UDFFillSource.MANUAL;
  }

  @Override
  public String toString() {
    return "UDFMetadataSecurity [categoryTypes=" + categoryTypes + ", specialInvestmentInstruments="
        + specialInvestmentInstruments + ", idUser=" + idUser + ", udfSpecialType=" + udfSpecialType + ", description="
        + description + ", descriptionHelp=" + descriptionHelp + ", udfDataType=" + udfDataType + ", fieldSize="
        + fieldSize + ", uiOrder=" + uiOrder + ", fillSource=" + fillSource + ", fillRuleVersion=" + fillRuleVersion + "]";
  }

}
