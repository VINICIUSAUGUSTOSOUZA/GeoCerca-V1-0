package com.geocerca.app;

public class QuoteConfig {
    public static final int MODE_PER_METER = 0;
    public static final int MODE_FIXED = 1;

    public static final int MATERIAL_CLIENT = 0;
    public static final int MATERIAL_PROVIDER = 1;

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

    public int materialResponsibility = MATERIAL_CLIENT;
    public double materialPrice = 0.0;

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

    public double materialTotal() {
        return materialResponsibility == MATERIAL_PROVIDER ? Math.max(0, materialPrice) : 0.0;
    }

    public double grandTotal(double fencedMeters, int strands) {
        return laborTotal(fencedMeters, strands) + materialTotal();
    }

    public String materialResponsibilityText() {
        return materialResponsibility == MATERIAL_PROVIDER
                ? "Material fornecido pelo prestador de serviços."
                : "Material fornecido pelo cliente.";
    }

    public String materialResponsibilityConditionText() {
        return materialResponsibility == MATERIAL_PROVIDER
                ? "Todos os materiais necessários à execução do cercamento serão adquiridos e fornecidos pelo prestador de serviços."
                : "Todos os materiais necessários à execução do cercamento são de responsabilidade do cliente.";
    }
}
