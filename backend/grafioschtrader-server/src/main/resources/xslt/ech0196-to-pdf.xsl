<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:eCH="http://www.ech.ch/xmlns/eCH-0196/2">

  <xsl:param name="barcodeImageUri"/>

  <xsl:template match="/eCH:taxStatement">
    <fo:root>
      <fo:layout-master-set>
        <fo:simple-page-master master-name="A4" page-width="210mm" page-height="297mm"
            margin-top="15mm" margin-bottom="15mm" margin-left="20mm" margin-right="20mm">
          <fo:region-body margin-top="25mm" margin-bottom="15mm"/>
          <fo:region-before extent="25mm"/>
          <fo:region-after extent="12mm"/>
        </fo:simple-page-master>
      </fo:layout-master-set>

      <fo:page-sequence master-reference="A4" font-family="Helvetica" font-size="9pt">
        <fo:static-content flow-name="xsl-region-before">
          <fo:block font-size="14pt" font-weight="bold" border-bottom="1pt solid black" padding-bottom="3mm">
            Steuerauszug / Relevé fiscal <xsl:value-of select="@taxPeriod"/>
          </fo:block>
        </fo:static-content>

        <fo:static-content flow-name="xsl-region-after">
          <fo:block text-align="center" font-size="7pt" color="#666666">
            Seite <fo:page-number/> von <fo:page-number-citation ref-id="last-page"/>
            &#160;|&#160; Erstellt: <xsl:value-of select="substring(@creationDate, 1, 10)"/>
            &#160;|&#160; Kanton: <xsl:value-of select="@canton"/>
          </fo:block>
        </fo:static-content>

        <fo:flow flow-name="xsl-region-body">
          <!-- Barcode -->
          <xsl:if test="$barcodeImageUri and $barcodeImageUri != ''">
            <fo:block text-align="right" margin-bottom="5mm">
              <fo:external-graphic src="{$barcodeImageUri}" content-width="25mm" content-height="25mm"/>
            </fo:block>
          </xsl:if>

          <!-- Institution + Client -->
          <fo:block margin-bottom="5mm">
            <fo:block font-weight="bold" font-size="10pt">
              <xsl:value-of select="eCH:institution/@name"/>
            </fo:block>
            <xsl:if test="eCH:institution/@lei">
              <fo:block>LEI: <xsl:value-of select="eCH:institution/@lei"/></fo:block>
            </xsl:if>
          </fo:block>

          <xsl:for-each select="eCH:client">
            <fo:block margin-bottom="3mm">
              Kunde: <xsl:value-of select="@clientNumber"/>
              <xsl:if test="@firstName or @lastName">
                &#160;- <xsl:value-of select="@firstName"/>&#160;<xsl:value-of select="@lastName"/>
              </xsl:if>
              <xsl:if test="@tin">
                &#160;(AHV: <xsl:value-of select="@tin"/>)
              </xsl:if>
            </fo:block>
          </xsl:for-each>

          <fo:block margin-bottom="3mm">
            Steuerperiode: <xsl:value-of select="@periodFrom"/> bis <xsl:value-of select="@periodTo"/>
          </fo:block>

          <!-- Securities section -->
          <xsl:if test="eCH:listOfSecurities">
            <fo:block font-weight="bold" font-size="11pt" margin-top="5mm" margin-bottom="3mm"
                border-bottom="0.5pt solid #333" padding-bottom="1mm">
              Wertschriftenverzeichnis
            </fo:block>

            <xsl:for-each select="eCH:listOfSecurities/eCH:depot">
              <fo:block margin-bottom="2mm" font-weight="bold" font-size="9pt">
                Depot: <xsl:value-of select="@depotNumber"/>
              </fo:block>

              <xsl:choose>
                <xsl:when test="eCH:security">
                  <fo:table table-layout="fixed" width="100%" border-collapse="collapse">
                    <fo:table-column column-width="8mm"/>
                    <fo:table-column column-width="28mm"/>
                    <fo:table-column column-width="42mm"/>
                    <fo:table-column column-width="12mm"/>
                    <fo:table-column column-width="10mm"/>
                    <fo:table-column column-width="20mm"/>
                    <fo:table-column column-width="25mm"/>
                    <fo:table-column column-width="25mm"/>

                    <fo:table-header>
                      <fo:table-row background-color="#E8E8E8" font-weight="bold" font-size="7pt">
                        <fo:table-cell padding="1mm" border="0.5pt solid #999"><fo:block>#</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999"><fo:block>Valor/ISIN</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999"><fo:block>Bezeichnung</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999"><fo:block>Land</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999"><fo:block>Whg</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999" text-align="right"><fo:block>Stück</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999" text-align="right"><fo:block>Steuerwert CHF</fo:block></fo:table-cell>
                        <fo:table-cell padding="1mm" border="0.5pt solid #999" text-align="right"><fo:block>Ertrag CHF</fo:block></fo:table-cell>
                      </fo:table-row>
                    </fo:table-header>

                    <fo:table-body>
                      <xsl:for-each select="eCH:security">
                        <fo:table-row font-size="7.5pt">
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC">
                            <fo:block><xsl:value-of select="@positionId"/></fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC">
                            <fo:block>
                              <xsl:if test="@valorNumber"><xsl:value-of select="@valorNumber"/><xsl:text> </xsl:text></xsl:if>
                              <xsl:value-of select="@isin"/>
                            </fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC">
                            <fo:block><xsl:value-of select="@securityName"/></fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC">
                            <fo:block><xsl:value-of select="@country"/></fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC">
                            <fo:block><xsl:value-of select="@currency"/></fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC" text-align="right">
                            <fo:block>
                              <xsl:if test="eCH:taxValue">
                                <xsl:value-of select="format-number(eCH:taxValue/@quantity, '#,##0.##')"/>
                              </xsl:if>
                            </fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC" text-align="right">
                            <fo:block>
                              <xsl:if test="eCH:taxValue/@value">
                                <xsl:value-of select="format-number(eCH:taxValue/@value, '#,##0.00')"/>
                              </xsl:if>
                            </fo:block>
                          </fo:table-cell>
                          <fo:table-cell padding="1mm" border="0.5pt solid #CCC" text-align="right">
                            <fo:block>
                              <xsl:variable name="paymentTotal" select="sum(eCH:payment/@amount)"/>
                              <xsl:if test="$paymentTotal != 0">
                                <xsl:value-of select="format-number($paymentTotal, '#,##0.00')"/>
                              </xsl:if>
                            </fo:block>
                          </fo:table-cell>
                        </fo:table-row>
                      </xsl:for-each>
                    </fo:table-body>
                  </fo:table>
                </xsl:when>
                <xsl:otherwise>
                  <fo:block font-style="italic" font-size="8pt" margin-bottom="3mm">
                    Keine Wertschriften in diesem Depot.
                  </fo:block>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:for-each>

            <!-- Totals -->
            <fo:block margin-top="5mm" font-weight="bold" border-top="1pt solid black" padding-top="2mm">
              <fo:table table-layout="fixed" width="100%">
                <fo:table-column column-width="85mm"/>
                <fo:table-column column-width="85mm"/>
                <fo:table-body>
                  <fo:table-row>
                    <fo:table-cell><fo:block>Total Steuerwert CHF:</fo:block></fo:table-cell>
                    <fo:table-cell text-align="right">
                      <fo:block><xsl:value-of select="format-number(eCH:listOfSecurities/@totalTaxValue, '#,##0.00')"/></fo:block>
                    </fo:table-cell>
                  </fo:table-row>
                  <fo:table-row>
                    <fo:table-cell><fo:block>Total Bruttoertrag A (mit VSt-Anspruch) CHF:</fo:block></fo:table-cell>
                    <fo:table-cell text-align="right">
                      <fo:block><xsl:value-of select="format-number(eCH:listOfSecurities/@totalGrossRevenueA, '#,##0.00')"/></fo:block>
                    </fo:table-cell>
                  </fo:table-row>
                  <fo:table-row>
                    <fo:table-cell><fo:block>Total Bruttoertrag B (ohne VSt-Anspruch) CHF:</fo:block></fo:table-cell>
                    <fo:table-cell text-align="right">
                      <fo:block><xsl:value-of select="format-number(eCH:listOfSecurities/@totalGrossRevenueB, '#,##0.00')"/></fo:block>
                    </fo:table-cell>
                  </fo:table-row>
                  <fo:table-row>
                    <fo:table-cell><fo:block>Total Verrechnungssteueranspruch CHF:</fo:block></fo:table-cell>
                    <fo:table-cell text-align="right">
                      <fo:block><xsl:value-of select="format-number(eCH:listOfSecurities/@totalWithHoldingTaxClaim, '#,##0.00')"/></fo:block>
                    </fo:table-cell>
                  </fo:table-row>
                </fo:table-body>
              </fo:table>
            </fo:block>
          </xsl:if>

          <fo:block id="last-page"/>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>
