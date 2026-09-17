package com.geocerca.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 1001;
    private static final int REQ_EXPORT = 1002;
    private static final String PREFS = "geocerca_prefs";

    private final List<GeoPoint> points = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private final FenceConfig config = new FenceConfig();
    private final QuoteConfig quote = new QuoteConfig();

    private FenceView fenceView;
    private TextView info;
    private boolean closed = false;
    private byte[] pendingPdf;
    private String sourceName = "Projeto";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPersistentResponsible();
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,18,18,18);

        TextView title = new TextView(this);
        title.setText("GeoCerca — Arame Farpado");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0,8,0,10);
        root.addView(title, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button importBtn = btn("Importar KML/TXT");
        Button clearBtn = btn("Apagar tudo");
        Button undoBtn = btn("Desfazer");
        Button closeBtn = btn("Fechar perímetro");
        row1.addView(importBtn, new LinearLayout.LayoutParams(0,-2,1));
        row1.addView(clearBtn, new LinearLayout.LayoutParams(0,-2,1));
        row1.addView(undoBtn, new LinearLayout.LayoutParams(0,-2,1));
        row1.addView(closeBtn, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button configBtn = btn("Configurar cerca");
        Button quoteBtn = btn("Dados / orçamento");
        Button calcBtn = btn("Materiais");
        row2.addView(configBtn, new LinearLayout.LayoutParams(0,-2,1));
        row2.addView(quoteBtn, new LinearLayout.LayoutParams(0,-2,1));
        row2.addView(calcBtn, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        Button pdfBtn = btn("Gerar orçamento PDF");
        Button shareBtn = btn("Compartilhar WhatsApp");
        row3.addView(pdfBtn, new LinearLayout.LayoutParams(0,-2,1));
        row3.addView(shareBtn, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row3);

        TextView hint = new TextView(this);
        hint.setText("TXT: toque nos pontos na ordem desejada e depois em FECHAR PERÍMETRO. KML: o perímetro é desenhado automaticamente.");
        hint.setTextSize(14);
        hint.setPadding(4,10,4,8);
        root.addView(hint);

        fenceView = new FenceView(this);
        fenceView.setConfig(config);
        fenceView.setPointTapListener(index -> {
            if (closed) { toast("O perímetro já está fechado. Use Desfazer para editar."); return; }
            if (order.contains(index)) { toast("Este ponto já faz parte da sequência."); return; }
            order.add(index);
            refresh();
        });
        root.addView(fenceView, new LinearLayout.LayoutParams(-1,0,1));

        info = new TextView(this);
        info.setTextSize(15);
        info.setPadding(8,12,8,8);
        root.addView(info, new LinearLayout.LayoutParams(-1,-2));

        importBtn.setOnClickListener(v -> importFile());
        clearBtn.setOnClickListener(v -> clearAll());
        undoBtn.setOnClickListener(v -> undo());
        closeBtn.setOnClickListener(v -> closePolygon());
        configBtn.setOnClickListener(v -> showFenceConfig());
        quoteBtn.setOnClickListener(v -> showQuoteConfig());
        calcBtn.setOnClickListener(v -> calculateAndShow());
        pdfBtn.setOnClickListener(v -> exportPdf());
        shareBtn.setOnClickListener(v -> sharePdfWhatsApp());
        refresh();
        setContentView(root);
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        return b;
    }

    private void importFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.google-earth.kml+xml","text/plain","application/octet-stream"});
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT) loadUri(uri);
        else if (requestCode == REQ_EXPORT && pendingPdf != null) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(pendingPdf);
                os.flush();
                toast("Orçamento em PDF salvo com sucesso.");
            } catch (Exception e) {
                error("Não foi possível salvar o PDF: " + e.getMessage());
            }
            pendingPdf = null;
        }
    }

    private void loadUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            sourceName = fileName(uri);
            boolean kml = sourceName.toLowerCase(Locale.ROOT).endsWith(".kml");
            List<GeoPoint> parsed = kml ? ImportParser.parseKml(in) : ImportParser.parseTxt(in);
            if (parsed.size() < 3) { error("Foram encontrados menos de 3 pontos válidos."); return; }
            points.clear();
            points.addAll(parsed);
            order.clear();
            closed = false;
            if (kml) {
                for (int x=0; x<points.size(); x++) order.add(x);
                closed = true;
                toast(points.size()+" vértices lidos do KML. Perímetro desenhado.");
            } else {
                toast(points.size()+" pontos lidos. Toque nos pontos para ligar o perímetro manualmente.");
            }
            refresh();
        } catch (Exception e) {
            error("Erro ao importar: " + e.getMessage());
        }
    }

    private String fileName(Uri uri) {
        String name = "Projeto";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private void clearAll() {
        if (points.isEmpty() && order.isEmpty()) {
            toast("Não há dados para apagar.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Apagar tudo")
                .setMessage("Deseja apagar todos os pontos e polígonos carregados? Depois você poderá importar outro KML/TXT.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", (d, w) -> {
                    points.clear();
                    order.clear();
                    closed = false;
                    sourceName = "Projeto";
                    pendingPdf = null;
                    refresh();
                    toast("Projeto limpo. Importe outro KML/TXT.");
                })
                .show();
    }

    private void undo() {
        if (closed) {
            closed = false;
            toast("Perímetro reaberto para edição.");
        } else if (!order.isEmpty()) {
            order.remove(order.size()-1);
        }
        refresh();
    }

    private void closePolygon() {
        if (order.size() < 3) { toast("Selecione pelo menos 3 vértices."); return; }
        closed = true;
        refresh();
        toast("Perímetro fechado.");
    }

    private List<GeoPoint> polygon() {
        List<GeoPoint> p = new ArrayList<>();
        for (int idx : order) p.add(points.get(idx));
        return p;
    }

    private void refresh() {
        if (fenceView != null) fenceView.setData(points, order, closed);
        if (info == null) return;
        if (!closed || order.size() < 3) {
            info.setText("Pontos importados: "+points.size()+"  •  selecionados: "+order.size()+"  •  perímetro: ainda não fechado");
        } else {
            FenceCalculator.Result r = FenceCalculator.calculate(polygon(), config);
            double labor = quote.laborTotal(r.perimeterM, config.strands);
            double materials = quote.materialTotal();
            double total = quote.grandTotal(r.perimeterM, config.strands);
            String extra = quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER
                    ? "  •  materiais: "+money(materials)+"  •  total: "+money(total) : "";
            info.setText(String.format(Locale.getDefault(),
                    "Perímetro: %.2f m  •  mourões interm.: %d  •  fiadas: %d  •  mão de obra: %s%s",
                    r.perimeterM, r.intermediatePosts, config.strands, money(labor), extra));
        }
    }

    private void showFenceConfig() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = dialogBox();
        EditText spacing = numberField(box,"Espaçamento máximo dos mourões (m)",config.postSpacingM);
        EditText strands = numberField(box,"Quantidade de fiadas de arame",config.strands);
        EditText braces = numberField(box,"Mourões de travamento por canto",config.bracesPerCorner);
        EditText struts = numberField(box,"Escoras por canto",config.strutsPerCorner);
        EditText wireRes = numberField(box,"Reserva de arame (%)",config.wireReservePct);
        EditText roll = numberField(box,"Metros por rolo de arame",config.wireRollM);
        EditText stapleRes = numberField(box,"Reserva de grampos (%)",config.stapleReservePct);
        EditText perKg = numberField(box,"Grampos por kg",config.staplesPerKg);
        EditText pkgKg = numberField(box,"Peso do pacote de grampos (kg)",config.staplePackageKg);
        scroll.addView(box);

        new AlertDialog.Builder(this).setTitle("Configuração do cercamento")
                .setView(scroll).setNegativeButton("Cancelar",null)
                .setPositiveButton("Salvar",(d,w)->{
                    try {
                        config.postSpacingM = Math.max(.1,num(spacing));
                        config.strands = Math.max(1,(int)Math.round(num(strands)));
                        config.bracesPerCorner = Math.max(0,(int)Math.round(num(braces)));
                        config.strutsPerCorner = Math.max(0,(int)Math.round(num(struts)));
                        config.wireReservePct = Math.max(0,num(wireRes));
                        config.wireRollM = Math.max(1,num(roll));
                        config.stapleReservePct = Math.max(0,num(stapleRes));
                        config.staplesPerKg = Math.max(1,num(perKg));
                        config.staplePackageKg = Math.max(.1,num(pkgKg));
                        fenceView.setConfig(config);
                        refresh();
                    } catch(Exception e) {
                        toast("Confira os valores informados.");
                    }
                }).show();
    }

    private void showQuoteConfig() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = dialogBox();

        section(box, "DADOS DO PROJETO");
        EditText client = textField(box, "Cliente", quote.clientName);
        EditText property = textField(box, "Propriedade / terreno", quote.propertyName);
        EditText location = textField(box, "Local", quote.location);

        section(box, "RESPONSÁVEL PELO SERVIÇO");
        EditText responsible = textField(box, "Nome do responsável", quote.responsibleName);
        EditText phone = textField(box, "Telefone / WhatsApp", quote.phone);
        EditText email = textField(box, "E-mail", quote.email);
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        section(box, "ORÇAMENTO DA MÃO DE OBRA");
        TextView modeLabel = new TextView(this);
        modeLabel.setText("Modalidade");
        box.addView(modeLabel);
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton byMeter = new RadioButton(this);
        byMeter.setText("Por metro linear");
        RadioButton fixed = new RadioButton(this);
        fixed.setText("Valor fechado");
        modes.addView(byMeter);
        modes.addView(fixed);
        box.addView(modes);
        if (quote.mode == QuoteConfig.MODE_FIXED) fixed.setChecked(true); else byMeter.setChecked(true);

        EditText baseRate = numberField(box, "Valor base por metro (até 4 fiadas)", quote.basePricePerMeterUpTo4);
        EditText extraRate = numberField(box, "Adicional por fiada acima de 4 (R$/m)", quote.extraPricePerStrandPerMeter);
        EditText fixedPrice = numberField(box, "Valor fechado do serviço (R$)", quote.fixedPrice);

        CheckBox showBreakdown = new CheckBox(this);
        showBreakdown.setText("Mostrar composição do preço no PDF");
        showBreakdown.setChecked(quote.showPriceBreakdown);
        box.addView(showBreakdown);

        section(box, "RESPONSABILIDADE DOS MATERIAIS");
        RadioGroup materialGroup = new RadioGroup(this);
        materialGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton materialClient = new RadioButton(this);
        materialClient.setText("Material fornecido pelo cliente");
        RadioButton materialProvider = new RadioButton(this);
        materialProvider.setText("Material fornecido pelo prestador de serviços");
        materialGroup.addView(materialClient);
        materialGroup.addView(materialProvider);
        box.addView(materialGroup);
        if (quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER) materialProvider.setChecked(true);
        else materialClient.setChecked(true);

        EditText materialPrice = numberField(box, "Valor dos materiais a cobrar (R$)", quote.materialPrice);

        section(box, "CONDIÇÕES");
        EditText validity = numberField(box, "Validade do orçamento (dias)", quote.validityDays);
        EditText payment = textField(box, "Forma de pagamento", quote.paymentTerms);
        EditText execution = textField(box, "Prazo / condição de execução", quote.executionTerms);

        Runnable toggleLabor = () -> {
            boolean meter = byMeter.isChecked();
            baseRate.setEnabled(meter);
            extraRate.setEnabled(meter);
            fixedPrice.setEnabled(!meter);
        };
        modes.setOnCheckedChangeListener((g,id) -> toggleLabor.run());
        toggleLabor.run();

        Runnable toggleMaterials = () -> materialPrice.setEnabled(materialProvider.isChecked());
        materialGroup.setOnCheckedChangeListener((g,id) -> toggleMaterials.run());
        toggleMaterials.run();

        scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Dados e orçamento")
                .setView(scroll).setNegativeButton("Cancelar",null)
                .setPositiveButton("Salvar",(d,w)->{
                    try {
                        quote.clientName = text(client);
                        quote.propertyName = text(property);
                        quote.location = text(location);
                        quote.responsibleName = text(responsible);
                        quote.phone = text(phone);
                        quote.email = text(email);
                        quote.mode = fixed.isChecked() ? QuoteConfig.MODE_FIXED : QuoteConfig.MODE_PER_METER;
                        quote.basePricePerMeterUpTo4 = Math.max(0,num(baseRate));
                        quote.extraPricePerStrandPerMeter = Math.max(0,num(extraRate));
                        quote.fixedPrice = Math.max(0,num(fixedPrice));
                        quote.showPriceBreakdown = showBreakdown.isChecked();
                        quote.materialResponsibility = materialProvider.isChecked()
                                ? QuoteConfig.MATERIAL_PROVIDER : QuoteConfig.MATERIAL_CLIENT;
                        quote.materialPrice = quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER
                                ? Math.max(0,num(materialPrice)) : 0.0;
                        quote.validityDays = Math.max(1,(int)Math.round(num(validity)));
                        quote.paymentTerms = text(payment);
                        quote.executionTerms = text(execution);
                        savePersistentResponsible();
                        refresh();
                    } catch(Exception e) {
                        toast("Confira os valores informados.");
                    }
                }).show();
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32,12,32,24);
        return box;
    }

    private void section(LinearLayout box, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTextColor(Color.rgb(10,85,45));
        t.setPadding(0,20,0,6);
        box.addView(t);
    }

    private EditText numberField(LinearLayout box, String label, double value) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setPadding(0,8,0,0);
        box.addView(t);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(value == (long)value ? String.valueOf((long)value) : String.valueOf(value));
        box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        return e;
    }

    private EditText textField(LinearLayout box, String label, String value) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setPadding(0,8,0,0);
        box.addView(t);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(value == null ? "" : value);
        box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        return e;
    }

    private double num(EditText e) {
        String v = e.getText().toString().trim().replace("R$","").replace(" ","");
        if (v.isEmpty()) return 0;
        if (v.contains(",") && v.contains(".")) v = v.replace(".","").replace(',','.');
        else v = v.replace(',','.');
        return Double.parseDouble(v);
    }

    private String text(EditText e) {
        return e.getText().toString().trim();
    }

    private void calculateAndShow() {
        if (!closed || order.size() < 3) { toast("Feche o perímetro primeiro."); return; }
        FenceCalculator.Result r = FenceCalculator.calculate(polygon(),config);
        new AlertDialog.Builder(this).setTitle("Resumo do cercamento e orçamento")
                .setMessage(summary(r)).setPositiveButton("OK",null).show();
    }

    private String summary(FenceCalculator.Result r) {
        double labor = quote.laborTotal(r.perimeterM, config.strands);
        double materials = quote.materialTotal();
        double total = quote.grandTotal(r.perimeterM, config.strands);
        String budget;
        if (quote.mode == QuoteConfig.MODE_FIXED) {
            budget = "Modalidade: valor fechado\nValor da mão de obra: " + money(labor);
        } else {
            budget = String.format(Locale.getDefault(),
                    "Modalidade: por metro linear\nBase até 4 fiadas: %s/m\nFiadas extras: %d\nAdicional por fiada extra: %s/m\nValor final por metro: %s/m\nValor da mão de obra: %s",
                    money(quote.basePricePerMeterUpTo4), quote.extraStrands(config.strands),
                    money(quote.extraPricePerStrandPerMeter), money(quote.effectiveRatePerMeter(config.strands)), money(labor));
        }
        if (quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER) {
            budget += "\nValor dos materiais: " + money(materials) + "\nTOTAL GERAL: " + money(total);
        }
        return String.format(Locale.getDefault(),
                "Perímetro cercado: %.2f m\nFiadas de arame: %d\n\n"+
                "Mourões intermediários: %d un.\n"+
                "Mourões de canto: %d un.\n"+
                "Mourões de travamento: %d un.\n"+
                "Escoras: %d un.\n\n"+
                "Arame farpado (com %.1f%% de reserva): %.2f m\n"+
                "Rolos de %.0f m: %d un.\n\n"+
                "Grampos (com %.1f%% de reserva): %d un.\n"+
                "Peso estimado: %.2f kg\n"+
                "Pacotes de %.1f kg: %d un.\n\n"+
                "%s\n\n%s",
                r.perimeterM, config.strands, r.intermediatePosts, r.cornerPosts, r.bracePosts, r.struts,
                config.wireReservePct, r.wireM, config.wireRollM, r.wireRolls,
                config.stapleReservePct, r.staples, r.stapleKg, config.staplePackageKg, r.staplePackages,
                budget, quote.materialResponsibilityText());
    }

    private void exportPdf() {
        if (!closed || order.size() < 3) { toast("Feche o perímetro antes de gerar o PDF."); return; }
        try {
            pendingPdf = buildPdf();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            String base = safeBaseName();
            i.putExtra(Intent.EXTRA_TITLE,"GeoCerca_Orcamento_"+base+".pdf");
            startActivityForResult(i,REQ_EXPORT);
        } catch(Exception e) {
            error("Falha ao gerar PDF: "+e.getMessage());
        }
    }

    private void sharePdfWhatsApp() {
        if (!closed || order.size() < 3) { toast("Feche o perímetro antes de compartilhar o PDF."); return; }
        try {
            byte[] pdfBytes = buildPdf();
            File shareDir = new File(getCacheDir(), "shared_pdfs");
            if (!shareDir.exists() && !shareDir.mkdirs()) throw new Exception("Não foi possível preparar a pasta temporária.");
            File pdfFile = new File(shareDir, "GeoCerca_Orcamento_" + safeBaseName() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                fos.write(pdfBytes);
                fos.flush();
            }

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/pdf");
            send.putExtra(Intent.EXTRA_STREAM, contentUri);
            send.putExtra(Intent.EXTRA_TEXT, "Orçamento de cercamento GeoCerca com croqui e lista de materiais.");
            send.setClipData(ClipData.newRawUri("GeoCerca PDF", contentUri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setPackage("com.whatsapp");
            try {
                startActivity(send);
            } catch (ActivityNotFoundException e) {
                send.setPackage(null);
                startActivity(Intent.createChooser(send, "Compartilhar orçamento PDF"));
            }
        } catch (Exception e) {
            error("Falha ao compartilhar PDF: " + e.getMessage());
        }
    }

    private String safeBaseName() {
        return sourceName.replaceAll("(?i)\\.(kml|txt)$", "").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private byte[] buildPdf() throws Exception {
        FenceCalculator.Result r = FenceCalculator.calculate(polygon(),config);
        double labor = quote.laborTotal(r.perimeterM, config.strands);
        double materials = quote.materialTotal();
        double total = quote.grandTotal(r.perimeterM, config.strands);

        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int W = 1240, H = 1754;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(W,H,1).create());
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);

        int green = Color.rgb(5,78,41);
        int lightGreen = Color.rgb(235,246,238);
        int gray = Color.rgb(90,90,90);
        int line = Color.rgb(210,220,212);

        p.setColor(green); p.setTextSize(52); p.setFakeBoldText(true);
        c.drawText("GeoCerca",60,72,p);
        p.setTextSize(34);
        c.drawText("ORÇAMENTO DE CERCAMENTO",455,72,p);
        p.setFakeBoldText(false); p.setTextSize(20); p.setColor(gray);
        c.drawText("Croqui, quantitativos, mão de obra e materiais",455,103,p);
        p.setColor(green); p.setStrokeWidth(4); c.drawLine(60,125,1180,125,p);

        sectionPdf(c,p,"1  DADOS DO PROJETO",60,165,green);
        p.setTextSize(21); p.setColor(Color.BLACK);
        drawLabelValue(c,p,"Cliente:", safe(quote.clientName,"Não informado"),60,205,250);
        drawLabelValue(c,p,"Propriedade:", safe(quote.propertyName,"Não informada"),60,238,250);
        drawLabelValue(c,p,"Local:", safe(quote.location,"Não informado"),60,271,250);
        drawLabelValue(c,p,"Arquivo:", sourceName,60,304,250);

        drawLabelValue(c,p,"Responsável:", safe(quote.responsibleName,"Não informado"),650,205,810);
        drawLabelValue(c,p,"Telefone / WhatsApp:", safe(quote.phone,"Não informado"),650,238,870);
        drawLabelValue(c,p,"E-mail:", safe(quote.email,"Não informado"),650,271,810);
        drawLabelValue(c,p,"Data / validade:", today()+" / "+quote.validityDays+" dias",650,304,835);
        p.setColor(line); p.setStrokeWidth(2); c.drawLine(60,330,1180,330,p);

        sectionPdf(c,p,"2  CROQUI DO CERCAMENTO",60,370,green);
        Bitmap snap = fenceView.snapshot();
        Rect src = new Rect(0,0,snap.getWidth(),snap.getHeight());
        RectF dst = new RectF(120,405,1120,925);
        p.setColor(Color.WHITE); c.drawRect(dst,p);
        c.drawBitmap(snap,src,dst,p);
        p.setColor(lightGreen); c.drawRoundRect(new RectF(130,935,1110,985),12,12,p);
        p.setColor(Color.BLACK); p.setTextSize(21); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.format(Locale.getDefault(),
                "Perímetro: %.2f m   |   Fiadas: %d   |   Espaçamento máximo: %.2f m   |   Arame farpado",
                r.perimeterM,config.strands,config.postSpacingM),620,967,p);
        p.setTextAlign(Paint.Align.LEFT);

        sectionPdf(c,p,"3  LISTA ESTIMADA DE MATERIAIS",60,1030,green);
        float tableX=60, tableY=1060, tableW=555, rowH=39;
        p.setColor(green); c.drawRect(tableX,tableY,tableX+tableW,tableY+rowH,p);
        p.setColor(Color.WHITE); p.setTextSize(20); p.setFakeBoldText(true);
        c.drawText("Item",tableX+14,tableY+27,p); c.drawText("Quantidade",tableX+385,tableY+27,p);
        p.setFakeBoldText(false);
        String[][] mats = new String[][]{
                {"Mourões intermediários", r.intermediatePosts+" un."},
                {"Mourões de canto", r.cornerPosts+" un."},
                {"Mourões de travamento", r.bracePosts+" un."},
                {"Escoras", r.struts+" un."},
                {"Arame farpado", fmt(r.wireM)+" m"},
                {"Rolos de arame ("+fmt0(config.wireRollM)+" m)", r.wireRolls+" un."},
                {"Grampos", r.staples+" un."},
                {"Grampos estimados", fmt(r.stapleKg)+" kg"},
                {"Pacotes de grampo ("+fmt(config.staplePackageKg)+" kg)", r.staplePackages+" un."}
        };
        p.setTextSize(19);
        for (int i=0;i<mats.length;i++) {
            float yy=tableY+rowH*(i+1);
            p.setColor(i%2==0 ? Color.rgb(249,251,249) : Color.WHITE);
            c.drawRect(tableX,yy,tableX+tableW,yy+rowH,p);
            p.setColor(Color.BLACK);
            c.drawText(mats[i][0],tableX+14,yy+26,p);
            c.drawText(mats[i][1],tableX+390,yy+26,p);
        }
        p.setColor(Color.rgb(245,249,245));
        c.drawRoundRect(new RectF(60,1455,615,1518),10,10,p);
        p.setColor(green); p.setTextSize(17); p.setFakeBoldText(true);
        c.drawText(quote.materialResponsibilityText(),78,1482,p);
        p.setFakeBoldText(false); p.setColor(Color.DKGRAY); p.setTextSize(15);
        if (quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER) {
            c.drawText("Valor dos materiais: "+money(materials),78,1505,p);
        } else {
            c.drawText("Quantitativos estimados para planejamento e compra.",78,1505,p);
        }

        sectionPdf(c,p,"4  ORÇAMENTO",650,1030,green);
        RectF card = new RectF(650,1060,1180,1518);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(250,252,250)); c.drawRoundRect(card,14,14,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(green); c.drawRoundRect(card,14,14,p);
        p.setStyle(Paint.Style.FILL);

        float y=1100; p.setTextSize(20); p.setColor(Color.BLACK);
        if (quote.mode == QuoteConfig.MODE_FIXED) {
            drawQuoteLine(c,p,"Modalidade","Valor fechado",675,y,920); y+=42;
            drawQuoteLine(c,p,"Metragem considerada",fmt(r.perimeterM)+" m",675,y,920); y+=42;
        } else if (quote.showPriceBreakdown) {
            drawQuoteLine(c,p,"Modalidade","Por metro linear",675,y,920); y+=38;
            drawQuoteLine(c,p,"Base até 4 fiadas",money(quote.basePricePerMeterUpTo4)+" / m",675,y,920); y+=38;
            drawQuoteLine(c,p,"Fiadas excedentes",String.valueOf(quote.extraStrands(config.strands)),675,y,920); y+=38;
            drawQuoteLine(c,p,"Adicional / fiada",money(quote.extraPricePerStrandPerMeter)+" / m",675,y,920); y+=38;
            drawQuoteLine(c,p,"Valor final / metro",money(quote.effectiveRatePerMeter(config.strands))+" / m",675,y,920); y+=38;
            drawQuoteLine(c,p,"Metragem considerada",fmt(r.perimeterM)+" m",675,y,920); y+=38;
        } else {
            drawQuoteLine(c,p,"Modalidade","Mão de obra",675,y,920); y+=42;
            drawQuoteLine(c,p,"Metragem considerada",fmt(r.perimeterM)+" m",675,y,920); y+=42;
        }

        drawQuoteLine(c,p,"Mão de obra",money(labor),675,1320,920);
        if (quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER) {
            drawQuoteLine(c,p,"Materiais",money(materials),675,1355,920);
        }

        p.setColor(lightGreen); c.drawRoundRect(new RectF(675,1380,1155,1498),12,12,p);
        p.setColor(green); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true); p.setTextSize(21);
        c.drawText(quote.materialResponsibility == QuoteConfig.MATERIAL_PROVIDER
                ? "VALOR TOTAL DO ORÇAMENTO" : "VALOR TOTAL DA MÃO DE OBRA",915,1417,p);
        p.setTextSize(44); c.drawText(money(total),915,1475,p);
        p.setFakeBoldText(false); p.setTextAlign(Paint.Align.LEFT);

        p.setColor(line); p.setStrokeWidth(2); c.drawLine(60,1545,1180,1545,p);
        sectionPdf(c,p,"5  CONDIÇÕES",60,1582,green);
        p.setColor(Color.BLACK); p.setTextSize(18);
        c.drawText("• Forma de pagamento: "+safe(quote.paymentTerms,"A combinar"),70,1615,p);
        c.drawText("• Prazo de execução: "+safe(quote.executionTerms,"Conforme alinhamento entre as partes"),70,1645,p);
        c.drawText("• "+quote.materialResponsibilityConditionText(),70,1675,p);
        p.setTextSize(15); p.setColor(gray);
        c.drawText("GeoCerca — documento gerado automaticamente a partir do perímetro importado e das configurações do projeto.",60,1725,p);

        pdf.finishPage(page);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.writeTo(out);
        pdf.close();
        return out.toByteArray();
    }

    private void sectionPdf(Canvas c, Paint p, String text, float x, float y, int color) {
        p.setColor(color); p.setTextSize(26); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.LEFT);
        c.drawText(text,x,y,p); p.setFakeBoldText(false);
    }

    private void drawLabelValue(Canvas c, Paint p, String label, String value, float x, float y, float valueX) {
        p.setFakeBoldText(true); p.setColor(Color.BLACK); c.drawText(label,x,y,p);
        p.setFakeBoldText(false); c.drawText(value,valueX,y,p);
    }

    private void drawQuoteLine(Canvas c, Paint p, String label, String value, float x, float y, float valueX) {
        p.setColor(Color.DKGRAY); p.setFakeBoldText(false); c.drawText(label+":",x,y,p);
        p.setColor(Color.BLACK); p.setFakeBoldText(true); c.drawText(value,valueX,y,p); p.setFakeBoldText(false);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String today() {
        return new SimpleDateFormat("dd/MM/yyyy", new Locale("pt","BR")).format(new Date());
    }

    private String money(double value) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
        return nf.format(value);
    }

    private String fmt(double v) {
        return String.format(new Locale("pt","BR"),"%.2f",v);
    }

    private String fmt0(double v) {
        return String.format(new Locale("pt","BR"),"%.0f",v);
    }

    private void loadPersistentResponsible() {
        SharedPreferences sp = getSharedPreferences(PREFS,MODE_PRIVATE);
        quote.responsibleName = sp.getString("responsibleName","");
        quote.phone = sp.getString("phone","");
        quote.email = sp.getString("email","");
    }

    private void savePersistentResponsible() {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString("responsibleName",quote.responsibleName)
                .putString("phone",quote.phone)
                .putString("email",quote.email)
                .apply();
    }

    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private void error(String s) { new AlertDialog.Builder(this).setTitle("Atenção").setMessage(s).setPositiveButton("OK",null).show(); }
}
