package com.geocerca.app;

public class QuoteConfig {
    public static final int MODE_PER_METER = 0;
    public static final int MODE_FIXED = 1;

    public String clientName = "";
    public String propertyName = "";
    public String location = "";

    public String responsibleName = "";
    public String phone = "";
    public String email = "";

    public int mode = MODE_PER_METER;
    public double basePricePerMeterUpTo4 = 20.0;
    public double extraPricePerStrandPerMeter = 2.5;
    public double fixedPrice = 0.0;
    public boolean showPriceBreakdown = true;

    public int validityDays = 15;
    public String paymentTerms = "A combinar";
    public String executionTerms = "Conforme alinhamento entre as partes";

    public int extraStrands(int strands) {
        return Math.max(0, strands - 4);
    }

    public double effectiveRatePerMeter(int strands) {
        return Math.max(0, basePricePerMeterUpTo4)
                + extraStrands(strands) * Math.max(0, extraPricePerStrandPerMeter);
    }

    public double laborTotal(double fencedMeters, int strands) {
        if (mode == MODE_FIXED) return Math.max(0, fixedPrice);
        return Math.max(0, fencedMeters) * effectiveRatePerMeter(strands);
    }
}
