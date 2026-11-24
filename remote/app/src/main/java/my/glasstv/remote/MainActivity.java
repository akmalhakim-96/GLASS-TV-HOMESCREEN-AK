package my.glasstv.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

/** The one screen: connection, wallpapers, Home, prayer times, Bismillah, and HANTAR KE TV. */
public class MainActivity extends Activity {
    private static final int PICK_PHOTOS = 1, PICK_AZAN = 2, PICK_AZAN_SUBUH = 3, PICK_AZAN_WALLPAPER = 4;
    private static final int[] INTERVAL_S = {5, 15, 30, 60, 300, 1800, 3600, 86400};
    private static final String[] INTERVAL_LABEL = {"5 saat", "15 saat", "30 saat", "1 minit", "5 minit", "30 minit", "1 jam", "1 hari"};
    private static final String[] PRAYER_KEY = {"subuh", "zohor", "asar", "maghrib", "isyak"};
    private static final String[] PRAYER_LABEL = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final String DEFAULT_TEXT = "Telah masuk waktu solat fardu {waktu} bagi kawasan {zon} dan sekitarnya";
    private static final String DEFAULT_SUBTEXT = "Marilah menunaikan solat";
    private static final int MAX_DOCK = 5;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView status, result, photoCount, dockText, azanName, azanSubuhName;
    private EditText azanText, azanSubtext;
    /** The owner's TVs; the main one fills the form and answers Uji/Semak azan. */
    private TvList tvs;
    private String mainIp = "";
    private final java.util.Map<String, TvStatus> tvStatus = new java.util.HashMap<>();
    private LinearLayout tvRows;
    private RadioButton places, own;
    private Switch darken, fit, wifi, reduced, bismillah;
    private Spinner interval, columns, zone, dim;
    private LinearLayout thumbs, ownBox;
    private final android.widget.CompoundButton[] prayers = new android.widget.CompoundButton[5];
    private Button send;

    private final List<Uri> photos = new ArrayList<>();
    private List<String> dock;   // null: leave the TV's dock alone
    private Uri azan, azanSubuh, azanWallpaper;
    /** The form shows the TV's own settings (read on a successful connect); HANTAR waits for it. */
    private boolean filled;
    private boolean tvHasPhotos;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("remote", MODE_PRIVATE);
        try {
            ((TextView) findViewById(R.id.version)).setText("Versi " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {
        }
        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        photoCount = findViewById(R.id.photoCount);
        dockText = findViewById(R.id.dock);
        azanName = findViewById(R.id.azanName);
        azanSubuhName = findViewById(R.id.azanSubuhName);
        azanText = findViewById(R.id.azanText);
        azanSubtext = findViewById(R.id.azanSubtext);
        places = findViewById(R.id.places);
        own = findViewById(R.id.own);
        darken = findViewById(R.id.darken);
        fit = findViewById(R.id.fit);
        wifi = findViewById(R.id.wifi);
        reduced = findViewById(R.id.reduced);
        bismillah = findViewById(R.id.bismillah);
        interval = findViewById(R.id.interval);
        columns = findViewById(R.id.columns);
        zone = findViewById(R.id.zone);
        dim = findViewById(R.id.dim);
        thumbs = findViewById(R.id.thumbs);
        ownBox = findViewById(R.id.ownBox);
        send = findViewById(R.id.send);

        tvRows = findViewById(R.id.tvRows);
        tvs = TvList.fromJson(prefs.getString("tvs", null));
        mainIp = prefs.getString("ip", "");
        if (!mainIp.isEmpty() && tvs.find(mainIp) == null) tvs.addFound(java.util.Collections.singletonList(mainIp));
        saveTvs();
        azanText.setText(DEFAULT_TEXT);
        azanSubtext.setText(DEFAULT_SUBTEXT);
        interval.setAdapter(adapter(INTERVAL_LABEL));
        interval.setSelection(2);
        columns.setAdapter(adapter(new String[]{"4", "5", "6", "7", "8"}));
        columns.setSelection(1);
        dim.setAdapter(adapter(new String[]{"Ringan", "Sederhana", "Gelap"}));
        dim.setSelection(1);
        List<String> zoneLabels = new ArrayList<>();
        int sgr01 = 0;
        for (Zones.Zone z : Zones.all()) {
            if (z.code.equals("SGR01")) sgr01 = zoneLabels.size();
            zoneLabels.add(z.state + " · " + z.label());
        }
        zone.setAdapter(adapter(zoneLabels.toArray(new String[0])));
        zone.setSelection(sgr01);
        LinearLayout prayerBox = findViewById(R.id.prayers);
        String[] prayerIcon = {"🌅", "☀️", "🌤️", "🌇", "🌙"};
        float dpx = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 5; i++) {
            Switch sw = new Switch(this);
            sw.setText(prayerIcon[i] + "   " + PRAYER_LABEL[i]);
            sw.setTextColor(0xFF1E2340);
            sw.setTextSize(15);
            sw.setChecked(true);
            sw.setMinHeight(Math.round(46 * dpx));
            prayers[i] = sw;
            prayerBox.addView(sw);
        }
        zone.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                Zones.Zone z = Zones.all().get(pos);
                ((TextView) findViewById(R.id.solatSub)).setText(z.code + " · " + z.areas);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        setupTabs();
        places.setChecked(true);
        ownBox.setVisibility(View.GONE);
        ((android.widget.RadioGroup) findViewById(R.id.wallMode)).setOnCheckedChangeListener(
                (g, id) -> ownBox.setVisibility(id == R.id.own ? View.VISIBLE : View.GONE));

        findViewById(R.id.scan).setOnClickListener(v -> scan());
        findViewById(R.id.addIp).setOnClickListener(v -> addIpDialog());
        findViewById(R.id.selectAll).setOnClickListener(v -> {
            boolean all = tvs.selectedIps().size() < tvs.all().size();
            tvs.selectAll(all);
            saveTvs();
        });
        findViewById(R.id.checkAll).setOnClickListener(v -> checkAll());
        findViewById(R.id.pickPhotos).setOnClickListener(v -> pick("image/*", true, PICK_PHOTOS));
        findViewById(R.id.pickAzanWallpaper).setOnClickListener(v -> pick("image/*", false, PICK_AZAN_WALLPAPER));
        findViewById(R.id.pickAzan).setOnClickListener(v -> pick("audio/*", false, PICK_AZAN));
        findViewById(R.id.pickAzanSubuh).setOnClickListener(v -> pick("audio/*", false, PICK_AZAN_SUBUH));
        findViewById(R.id.pickDock).setOnClickListener(v -> pickDock());
        findViewById(R.id.setupHome).setOnClickListener(v -> confirmSetupSelected());
        findViewById(R.id.testAzan).setOnClickListener(v -> runOnTv(TvCommands.AZAN_TEST,
                "Azan diuji di TV. Tekan OK pada remote untuk kembali.", false));
        findViewById(R.id.checkAzan).setOnClickListener(v -> runOnTv(TvCommands.AZAN_REPORT, "Semakan azan", true));
        send.setOnClickListener(v -> send(Settings.ALL));
        findViewById(R.id.sendWallpaper).setOnClickListener(v -> send(Settings.WALLPAPER));
        findViewById(R.id.sendHome).setOnClickListener(v -> send(Settings.HOME));
        findViewById(R.id.sendPrayer).setOnClickListener(v -> send(Settings.PRAYER));
        findViewById(R.id.sendBismillah).setOnClickListener(v -> send(Settings.BISMILLAH));

        Spinner tvType = findViewById(R.id.tvType);
        String[] typeLabels = new String[TvTypes.ALL.length];
        int chosenType = 0;
        for (int i = 0; i < TvTypes.ALL.length; i++) {
            typeLabels[i] = TvTypes.ALL[i].label;
            if (TvTypes.ALL[i].key.equals(prefs.getString("tvType", "mi"))) chosenType = i;
        }
        tvType.setAdapter(adapter(typeLabels));
        tvType.setSelection(chosenType);
        tvType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                applyTvType(TvTypes.ALL[pos]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        applyTvType(TvTypes.ALL[chosenType]);

        restore(b);
        // With a remembered TV, read its settings straight away so the form never starts from guesses.
        if (!host().isEmpty() && TvTypes.ALL[chosenType].supported) connect();
    }

    /** Picks survive the phone closing the app while the photo or file picker is open. */
    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelableArrayList("photos", new ArrayList<>(photos));
        out.putParcelable("azan", azan);
        out.putParcelable("azanSubuh", azanSubuh);
        out.putParcelable("azanWallpaper", azanWallpaper);
        if (dock != null) out.putStringArrayList("dock", new ArrayList<>(dock));
    }

    private void restore(Bundle b) {
        if (b == null) return;
        ArrayList<Uri> saved = b.getParcelableArrayList("photos");
        if (saved != null) for (Uri u : saved) {
            photos.add(u);
            addThumb(u);
        }
        updatePhotoCount();
        azan = b.getParcelable("azan");
        if (azan != null) azanName.setText("Azan: " + name(azan));
        azanWallpaper = b.getParcelable("azanWallpaper");
        if (azanWallpaper != null) ((TextView) findViewById(R.id.azanWallpaperName)).setText("Gambar latar azan: " + name(azanWallpaper));
        azanSubuh = b.getParcelable("azanSubuh");
        if (azanSubuh != null) azanSubuhName.setText("Azan Subuh: " + name(azanSubuh));
        ArrayList<String> d = b.getStringArrayList("dock");
        if (d != null) {
            dock = d;
            List<String> names = new ArrayList<>();
            for (String p : d) names.add(TvApps.label(p));
            dockText.setText("Dock: " + String.join(", ", names));
        }
    }

    private ArrayAdapter<String> adapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    /** The main TV: the one the form is read from, and where Uji/Semak azan go. */
    private String host() {
        return mainIp == null ? "" : mainIp;
    }

    // ---- the TV list -------------------------------------------------------------------------------

    private void saveTvs() {
        prefs.edit().putString("tvs", tvs.toJson()).putString("ip", host()).apply();
        renderTvs();
    }

    /** One row per TV: icon, name, address, a coloured state label and the tick; the main TV is outlined. */
    private void renderTvs() {
        tvRows.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        int latest = 0, old = 0;
        for (TvList.Entry e : tvs.all()) {
            TvStatus st = tvStatus.get(e.ip);
            if (st != null && st.kind == TvStatus.Kind.LATEST) latest++;
            if (st != null && (st.kind == TvStatus.Kind.OUTDATED || st.kind == TvStatus.Kind.NOT_INSTALLED)) old++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.row_main);
            row.setActivated(e.ip.equals(host()));
            int pad = Math.round(8 * dp);
            row.setPadding(pad, pad, pad / 2, pad);

            TextView icon = new TextView(this);
            icon.setText("📺");
            icon.setTextSize(18);
            icon.setGravity(android.view.Gravity.CENTER);
            icon.setBackgroundResource(R.drawable.icon_box);
            row.addView(icon, new LinearLayout.LayoutParams(Math.round(40 * dp), Math.round(40 * dp)));

            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Math.round(12 * dp), 0, Math.round(6 * dp), 0);
            TextView name = new TextView(this);
            name.setText(e.name + (e.ip.equals(host()) ? "  ★" : ""));
            name.setTextColor(0xFF1E2340);
            name.setTextSize(14.5f);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(name);
            TextView sub = new TextView(this);
            sub.setText(e.ip + (e.ip.equals(host()) ? " · TV utama" : ""));
            sub.setTextColor(0xFF8A90A8);
            sub.setTextSize(11.5f);
            words.addView(sub);
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

            if (st != null) {
                TextView chip = new TextView(this);
                int[] col = TvStatus.chipColors(st);
                chip.setText(TvStatus.chip(st));
                chip.setTextColor(col[0]);
                chip.setTextSize(10.5f);
                chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(col[1]);
                bg.setCornerRadius(20 * dp);
                chip.setBackground(bg);
                chip.setPadding(Math.round(9 * dp), Math.round(4 * dp), Math.round(9 * dp), Math.round(4 * dp));
                row.addView(chip);
            }
            CheckBox tick = new CheckBox(this);
            tick.setChecked(e.selected);
            tick.setOnCheckedChangeListener((b, on) -> {
                tvs.setSelected(e.ip, on);
                prefs.edit().putString("tvs", tvs.toJson()).apply();
                updateSendLabel();
                ((TextView) findViewById(R.id.selectAll)).setText(
                        tvs.selectedIps().size() < tvs.all().size() ? "Pilih semua" : "Nyahpilih");
            });
            row.addView(tick);
            row.setOnClickListener(v -> {
                mainIp = e.ip;
                filled = false;
                saveTvs();
                connect();
            });
            row.setOnLongClickListener(v -> {
                tvMenu(e);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Math.round(4 * dp);
            tvRows.addView(row, lp);
        }
        findViewById(R.id.tvEmpty).setVisibility(tvs.all().isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.selectAll)).setText(
                tvs.selectedIps().size() < tvs.all().size() ? "Pilih semua" : "Nyahpilih");
        ((TextView) findViewById(R.id.statTotal)).setText(stat(tvs.all().size(), "TV disimpan"));
        ((TextView) findViewById(R.id.statLatest)).setText(stat(latest, "Terkini"));
        ((TextView) findViewById(R.id.statOld)).setText(stat(old, "Perlu kemas kini"));
        updateSendLabel();
    }

    private static CharSequence stat(int n, String what) {
        android.text.SpannableString s = new android.text.SpannableString(n + "\n" + what);
        int end = String.valueOf(n).length();
        s.setSpan(new android.text.style.RelativeSizeSpan(1.75f), 0, end, 0);
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, end, 0);
        return s;
    }

    private void updateSendLabel() {
        int n = tvs.selectedIps().size();
        ((Button) findViewById(R.id.send)).setText(n == 0 ? "Tanda TV untuk hantar" : "Hantar semua ke " + n + " TV");
    }

    // ---- tabs --------------------------------------------------------------------------------------

    private static final int[] TABS = {R.id.tabTv, R.id.tabGambar, R.id.tabHome, R.id.tabSolat};
    private static final int[] NAVS = {R.id.navTv, R.id.navGambar, R.id.navHome, R.id.navSolat};
    private static final int[] TAB_COLOR = {0xFF4F6BFF, 0xFF12B886, 0xFFFF7A3C, 0xFFFF5A5F};

    private void setupTabs() {
        for (int i = 0; i < NAVS.length; i++) {
            final int tab = i;
            findViewById(NAVS[i]).setOnClickListener(v -> showTab(tab));
        }
        showTab(prefs.getInt("tab", 0));
    }

    private void showTab(int tab) {
        for (int i = 0; i < TABS.length; i++) {
            findViewById(TABS[i]).setVisibility(i == tab ? View.VISIBLE : View.GONE);
            TextView nav = findViewById(NAVS[i]);
            nav.setTextColor(i == tab ? TAB_COLOR[i] : 0xFF9AA0B8);
            nav.setTypeface(i == tab ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        }
        getWindow().setStatusBarColor(TAB_COLOR[tab]);
        prefs.edit().putInt("tab", tab).apply();
    }

    private void tvMenu(TvList.Entry e) {
        new AlertDialog.Builder(this).setTitle(e.name + " (" + e.ip + ")")
                .setItems(new String[]{"Tukar nama", "Jadikan TV utama", "Buang dari senarai",
                        "Pair (Android 11+ Wireless debugging)"}, (d, w) -> {
                    if (w == 3) {
                        pairDialog(e);
                    } else if (w == 0) {
                        EditText in = new EditText(this);
                        in.setText(e.name);
                        in.setSelection(e.name.length());
                        new AlertDialog.Builder(this).setTitle("Nama TV").setView(in)
                                .setPositiveButton("Simpan", (x, y) -> {
                                    tvs.rename(e.ip, in.getText().toString());
                                    saveTvs();
                                }).setNegativeButton("Batal", null).show();
                    } else if (w == 1) {
                        mainIp = e.ip;
                        filled = false;
                        saveTvs();
                        connect();
                    } else {
                        tvs.remove(e.ip);
                        if (e.ip.equals(mainIp)) mainIp = tvs.all().isEmpty() ? "" : tvs.all().get(0).ip;
                        saveTvs();
                    }
                }).show();
    }

    /**
     * Wireless debugging pairing: on the TV, Developer options → Wireless debugging → "Pair device with
     * pairing code" shows a 6-digit code and "IP address & Port"; the port is found by itself when the
     * Wi-Fi allows, otherwise typed from that screen.
     */
    private void pairDialog(TvList.Entry e) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        box.setPadding(pad, (int) (8 * dp), pad, 0);
        TextView how = new TextView(this);
        how.setText("Di TV: Settings → Developer options → Wireless debugging → \"Pair device with pairing code\". "
                + "Biarkan tetingkap kod itu terbuka, kemudian taip kodnya di sini.\n"
                + "(Bila TV tanya, tanda \"Always allow on this network\" supaya Wireless debugging kekal ON.)");
        box.addView(how);
        EditText code = new EditText(this);
        code.setHint("Kod 6 nombor");
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(code);
        EditText port = new EditText(this);
        port.setHint("Port (nombor selepas \":\" di TV) – boleh kosong");
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(port);
        new AlertDialog.Builder(this).setTitle("Pair " + e.name + " (" + e.ip + ")").setView(box)
                .setPositiveButton("Pair", (d, w) -> {
                    String c = code.getText().toString().trim();
                    if (!AdbPairing.validCode(c)) {
                        status.setText("Kod pairing mesti 6 nombor, seperti di skrin TV.");
                        return;
                    }
                    int p;
                    try {
                        p = port.getText().toString().trim().isEmpty() ? -1 : Integer.parseInt(port.getText().toString().trim());
                    } catch (NumberFormatException ex) {
                        p = -1;
                    }
                    final int pairPort = p;
                    status.setText("Pairing dengan " + e.name + "…");
                    worker.execute(() -> {
                        String msg;
                        try {
                            Tv.pair(this, e.ip, pairPort, c);
                            msg = "✓ " + e.name + " sudah di-pair. Tekan Semak semua.";
                        } catch (Exception ex) {
                            msg = "PAIRING_PORT".equals(ex.getMessage())
                                    ? "Port pairing tidak dijumpai sendiri (Wi-Fi ini menyekatnya). Taip nombor port yang tertera di TV selepas tanda \":\"."
                                    : Messages.forError(ex);
                        }
                        String m = msg;
                        runOnUiThread(() -> status.setText(m));
                    });
                }).setNegativeButton("Batal", null).show();
    }

    private void addIpDialog() {
        EditText in = new EditText(this);
        in.setHint("contoh 192.168.0.12");
        in.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        new AlertDialog.Builder(this).setTitle("Tambah TV").setView(in)
                .setPositiveButton("Tambah", (d, w) -> {
                    String ipText = in.getText().toString().trim();
                    if (ipText.isEmpty()) return;
                    tvs.addFound(java.util.Collections.singletonList(ipText));
                    if (host().isEmpty()) mainIp = ipText;
                    saveTvs();
                }).setNegativeButton("Batal", null).show();
    }

    /** Each TV's state, one after another; the first time, each TV asks "Allow USB debugging?". */
    private void checkAll() {
        List<TvList.Entry> all = new ArrayList<>(tvs.all());
        if (all.isEmpty()) {
            status.setText("Belum ada TV. Tekan Cari semua TV.");
            return;
        }
        status.setText("Menyemak " + all.size() + " TV… Jika TV bertanya, tanda Always allow dan tekan OK.");
        worker.execute(() -> {
            int i = 0;
            for (TvList.Entry e : all) {
                final int n = ++i;
                runOnUiThread(() -> status.setText("Menyemak " + n + "/" + all.size() + ": " + e.name + "…"));
                TvStatus label;
                try (Tv tv = Tv.connect(this, e.ip)) {
                    label = TvStatus.of(TvVersion.parse(tv.shell(TvVersion.QUERY)), TvVersion.BUNDLED);
                } catch (Exception ex) {
                    label = TvStatus.fromError(ex);
                }
                final TvStatus l = label;
                runOnUiThread(() -> {
                    tvStatus.put(e.ip, l);
                    renderTvs();
                });
            }
            String phone = Discovery.phoneIp(this);
            StringBuilder note = new StringBuilder("Semakan selesai. IP telefon: " + (phone == null ? "tiada Wi-Fi" : phone) + ".");
            for (TvList.Entry e : all) {
                if (phone != null && !Subnet.sameNetwork(phone, e.ip)) {
                    note.append("\n⚠️ ").append(e.name).append(" (").append(e.ip).append(") di rangkaian lain daripada telefon.");
                }
            }
            boolean anyNoAnswer = false;
            for (TvList.Entry e : all) {
                TvStatus st = tvStatus.get(e.ip);
                if (st != null && st.kind == TvStatus.Kind.UNREACHABLE && phone != null && Subnet.sameNetwork(phone, e.ip)) anyNoAnswer = true;
            }
            if (anyNoAnswer) note.append("\n\"Tiada\" pada rangkaian sama: TV mungkin tidur, atau Wi-Fi menyekat peranti berhubung sesama sendiri (biasa di sekolah/pejabat).");
            runOnUiThread(() -> status.setText(note.toString()));
        });
    }

    // ---- azan test and check -----------------------------------------------------------------------

    /** Runs one command on the TV; with showOutput, the answer opens in the copyable report dialog. */
    private void runOnTv(String cmd, String title, boolean showOutput) {
        String h = host();
        if (h.isEmpty()) {
            status.setText("Sambung ke TV dahulu.");
            return;
        }
        status.setText("Bersambung ke " + h + "…");
        worker.execute(() -> {
            try (Tv tv = Tv.connect(this, h)) {
                String out = tv.shell(cmd);
                runOnUiThread(() -> {
                    status.setText(showOutput ? "Bersambung ✓ " + h : title);
                    if (showOutput) showReport(title, out.trim().isEmpty() ? "(tiada jawapan)" : out.trim());
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(Messages.forError(e)));
            }
        });
    }

    // ---- TV type ---------------------------------------------------------------------------------

    /** Samsung and LG cannot run Glass TV: say why and what works instead, and turn the buttons off. */
    private void applyTvType(TvTypes.Type t) {
        prefs.edit().putString("tvType", t.key).apply();
        ((TextView) findViewById(R.id.tvTypeNote)).setText(t.note);
        for (int id : new int[]{R.id.scan, R.id.addIp, R.id.checkAll, R.id.setupHome, R.id.send, R.id.sendWallpaper, R.id.sendHome,
                R.id.sendPrayer, R.id.sendBismillah, R.id.testAzan, R.id.checkAzan}) findViewById(id).setEnabled(t.supported);
        if (!t.supported) status.setText("Glass TV tidak boleh dipasang pada " + t.label + ".");
    }

    private void showDetected(TvTypes.Type t, String release) {
        Spinner tvType = findViewById(R.id.tvType);
        for (int i = 0; i < TvTypes.ALL.length; i++) if (TvTypes.ALL[i] == t) tvType.setSelection(i);
        // After the spinner's own listener (posted), which would otherwise put the generic note back.
        tvType.post(() -> ((TextView) findViewById(R.id.tvTypeNote)).setText("Dikesan: " + t.label + " · Android " + release.trim()));
    }

    // ---- connection ------------------------------------------------------------------------------

    /** Every TV answering on the Wi-Fi joins the list (ticked); the first becomes the main TV. */
    private void scan() {
        status.setText("Mencari TV dalam Wi-Fi…");
        worker.execute(() -> {
            if (Discovery.phoneIp(this) == null) {
                runOnUiThread(() -> status.setText(Messages.NO_WIFI));
                return;
            }
            List<String> found = Discovery.scan(this);
            runOnUiThread(() -> {
                if (found.isEmpty()) {
                    status.setText(Messages.NOT_FOUND);
                    return;
                }
                int before = tvs.all().size();
                tvs.addFound(found);
                status.setText(found.size() + " TV dijumpai, " + (tvs.all().size() - before) + " baharu ditambah.");
                if (host().isEmpty()) {
                    mainIp = tvs.all().get(0).ip;
                    saveTvs();
                    connect();
                } else {
                    saveTvs();
                }
            });
        });
    }

    /** Connects, then fills the form from the TV's current config.json. */
    private void connect() {
        String h = host();
        if (h.isEmpty()) {
            status.setText("Tekan Cari semua TV atau + IP.");
            return;
        }
        status.setText("Bersambung ke " + h + "… Jika TV bertanya, tanda Always allow dan tekan OK.");
        worker.execute(() -> {
            try (Tv tv = Tv.connect(this, h)) {
                JSONObject cfg = ConfigMerge.read(tv.readConfig());
                int pictures = 0;
                try {
                    pictures = Integer.parseInt(tv.shell(TvCommands.countWallpapers()).trim());
                } catch (NumberFormatException ignored) {
                }
                final int onTv = pictures;
                TvTypes.Type detected = TvTypes.detect(tv.shell("getprop ro.product.manufacturer"),
                        tv.shell("getprop ro.product.model"),
                        tv.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"));
                String release = tv.shell("getprop ro.build.version.release");
                int glass = TvVersion.parse(tv.shell(TvVersion.QUERY));
                prefs.edit().putString("ip", h).apply();
                runOnUiThread(() -> {
                    tvStatus.put(h, TvStatus.of(glass, TvVersion.BUNDLED));
                    renderTvs();
                    showDetected(detected, release);
                    if (glass < 0) {
                        status.setText("Bersambung ✓ " + h + ". Glass TV belum dipasang: tekan Pasang & jadikan Home.");
                    } else if (TvVersion.needsUpdate(glass)) {
                        status.setText("Bersambung ✓ " + h + ". Glass TV di TV versi lama (" + glass + "): tekan Pasang & jadikan Home untuk kemas kini ke versi " + TvVersion.BUNDLED + ".");
                    } else {
                        status.setText("Bersambung ✓ " + h + " · Glass TV versi " + glass + " (terkini)");
                    }
                    tvHasPhotos = onTv > 0;
                    fill(cfg);
                    filled = true;
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(Messages.forError(e)));
            }
        });
    }

    private void fill(JSONObject c) {
        if (c.has("places")) {
            boolean p = c.optBoolean("places", true);
            places.setChecked(p);
            own.setChecked(!p);
        }
        int sec = c.optInt("wallpaper_seconds", 0);
        for (int i = 0; i < INTERVAL_S.length; i++) if (INTERVAL_S[i] == sec) interval.setSelection(i);
        int col = c.optInt("columns", 0);
        if (col >= 4 && col <= 8) columns.setSelection(col - 4);
        wifi.setChecked(c.optBoolean("show_network_pill", true));
        reduced.setChecked(c.optBoolean("reduced_motion", reduced.isChecked()));
        ((Switch) findViewById(R.id.keepOn)).setChecked(c.optBoolean("keep_screen_on", false));
        bismillah.setChecked(c.optBoolean("bismillah", true));
        String z = c.optString("prayer_zone", "");
        List<Zones.Zone> all = Zones.all();
        for (int i = 0; i < all.size(); i++) if (all.get(i).code.equalsIgnoreCase(z)) zone.setSelection(i);
        JSONArray ap = c.optJSONArray("azan_prayers");
        if (ap != null) {
            for (int i = 0; i < 5; i++) {
                boolean on = false;
                for (int j = 0; j < ap.length(); j++) on |= PRAYER_KEY[i].equals(ap.optString(j));
                prayers[i].setChecked(on);
            }
        }
        if (c.has("azan_text")) azanText.setText(c.optString("azan_text"));
        if (c.has("azan_subtext")) azanSubtext.setText(c.optString("azan_subtext"));
        int d = c.optInt("azan_dim", 1);
        if (d >= 0 && d <= 2) dim.setSelection(d);
        JSONArray da = c.optJSONArray("dock_apps");
        if (da != null && dock == null) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < da.length(); i++) names.add(TvApps.label(da.optString(i)));
            dockText.setText("Dock sekarang: " + String.join(", ", names));
        }
    }

    // ---- install and make Home ---------------------------------------------------------------------

    private void confirmSetupSelected() {
        List<String> chosen = tvs.selectedIps();
        if (chosen.isEmpty()) {
            status.setText("Tanda sekurang-kurangnya satu TV.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Pasang / kemas kini Glass TV pada " + chosen.size() + " TV?")
                .setMessage("Glass TV akan dipasang (atau dikemas kini) dan dijadikan Home pada setiap TV yang ditanda, "
                        + "satu demi satu. Home asal dimatikan (tidak dibuang); jika ada yang tidak kena pada sesebuah TV, "
                        + "Home asalnya dikembalikan dengan sendiri.\n\n"
                        + "Jika TV bertanya \"Allow USB debugging?\", tanda Always allow dan tekan OK. ±1 minit setiap TV.")
                .setPositiveButton("Pasang", (d, w) -> setupHomeAll(chosen))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void setupHomeAll(List<String> ips) {
        findViewById(R.id.setupHome).setEnabled(false);
        worker.execute(() -> {
            Batch batch = new Batch();
            StringBuilder reports = new StringBuilder();
            int i = 0;
            for (String h : ips) {
                TvList.Entry e = tvs.find(h);
                String label = e != null ? e.name : h;
                final String prefix = "(" + (++i) + "/" + ips.size() + ") " + label + ": ";
                String line = HomeSetup.run(this, h, step -> runOnUiThread(() -> status.setText(prefix + step)));
                batch.add(label, line);
                if (HomeSetup.lastReport != null) reports.append("\n\n— ").append(label).append(" —\n").append(HomeSetup.lastReport);
                if (line.startsWith("Siap")) runOnUiThread(() -> tvStatus.put(h, TvStatus.of(TvVersion.BUNDLED, TvVersion.BUNDLED)));
            }
            runOnUiThread(() -> {
                findViewById(R.id.setupHome).setEnabled(true);
                renderTvs();
                status.setText("Pasang: " + batch.headline());
                showReport("Pasang: " + batch.headline(), batch.details() + reports);
                filled = false;   // Glass TV may be new on the main TV: read its settings again before HANTAR
                if (!host().isEmpty()) connect();
            });
        });
    }

    /** Why the install was rolled back, with a copy button so the owner can paste it for help. */
    private void showReport(String report) {
        showReport("Pemasangan dibatalkan", report);
    }

    private void showReport(String title, String report) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(report)
                .setPositiveButton("Salin butiran", (d, w) -> {
                    android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Glass TV", report));
                    Toast.makeText(this, "Disalin. Tampal dalam chat.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /** Reads the TV's settings into the form, keeping the given line in the status. */
    private void connectQuietly(String keep) {
        connect();
        status.postDelayed(() -> {
            if (filled) status.setText(keep);
        }, 3000);
    }

    // ---- choosing --------------------------------------------------------------------------------

    private void pick(String type, boolean many, int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
        if (many) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, request);
    }

    @Override
    protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (code != RESULT_OK || data == null) return;
        if (request == PICK_PHOTOS) {
            List<Uri> picked = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) picked.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                picked.add(data.getData());
            }
            for (Uri u : picked) {
                if (photos.size() >= PhotoMath.MAX_PHOTOS) {
                    Toast.makeText(this, "Maksimum " + PhotoMath.MAX_PHOTOS + " foto", Toast.LENGTH_SHORT).show();
                    break;
                }
                photos.add(u);
                addThumb(u);
            }
            updatePhotoCount();
        } else if (request == PICK_AZAN_WALLPAPER) {
            azanWallpaper = data.getData();
            ((TextView) findViewById(R.id.azanWallpaperName)).setText("Gambar latar azan: " + name(azanWallpaper));
        } else if (request == PICK_AZAN) {
            azan = data.getData();
            azanName.setText("Azan: " + name(azan));
        } else if (request == PICK_AZAN_SUBUH) {
            azanSubuh = data.getData();
            azanSubuhName.setText("Azan Subuh: " + name(azanSubuh));
        }
    }

    private void addThumb(Uri u) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(128 * dp), Math.round(72 * dp));
        lp.rightMargin = Math.round(6 * dp);
        thumbs.addView(v, lp);
        v.setOnLongClickListener(x -> {
            int i = thumbs.indexOfChild(v);
            if (i >= 0) {
                photos.remove(i);
                thumbs.removeViewAt(i);
                updatePhotoCount();
            }
            return true;
        });
        worker.execute(() -> {
            Bitmap b = thumbnail(u);
            if (b != null) runOnUiThread(() -> v.setImageBitmap(b));
        });
    }

    /** A small decode for the strip; the full-size work happens only when sending. */
    private Bitmap thumbnail(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (o.outWidth / (s * 2) >= 256) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                return BitmapFactory.decodeStream(in, null, d);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void updatePhotoCount() {
        photoCount.setText(photos.isEmpty() ? "Tiada foto dipilih. Tekan lama pada foto untuk buang."
                : photos.size() + " foto dipilih. Tekan lama pada foto untuk buang.");
    }

    private String name(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return u.getLastPathSegment();
    }

    /** The TV's apps, chosen in the order they are ticked, at most five. */
    private void pickDock() {
        String h = host();
        if (h.isEmpty()) {
            status.setText("Sambung ke TV dahulu.");
            return;
        }
        status.setText("Membaca senarai app TV…");
        worker.execute(() -> {
            try (Tv tv = Tv.connect(this, h)) {
                List<String> pkgs = TvApps.parse(tv.shell(TvCommands.LIST_APPS));
                runOnUiThread(() -> showDockDialog(pkgs));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(Messages.forError(e)));
            }
        });
    }

    private void showDockDialog(List<String> pkgs) {
        status.setText("Bersambung ✓ " + host());
        String[] labels = new String[pkgs.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = TvApps.label(pkgs.get(i));
        List<String> chosen = new ArrayList<>();
        boolean[] checked = new boolean[labels.length];
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Pilih hingga " + MAX_DOCK + " app (ikut susunan tekan)")
                .setMultiChoiceItems(labels, checked, null)
                .setPositiveButton("OK", (x, w) -> {
                    dock = chosen;
                    List<String> names = new ArrayList<>();
                    for (String p : chosen) names.add(TvApps.label(p));
                    dockText.setText(chosen.isEmpty() ? "Dock: tidak diubah" : "Dock: " + String.join(", ", names));
                    if (chosen.isEmpty()) dock = null;
                })
                .setNegativeButton("Batal", null)
                .create();
        d.setOnShowListener(x -> {
            ListView list = d.getListView();
            list.setOnItemClickListener((parent, view, pos, id) -> {
                String p = pkgs.get(pos);
                if (chosen.contains(p)) {
                    chosen.remove(p);
                } else if (chosen.size() >= MAX_DOCK) {
                    list.setItemChecked(pos, false);
                    Toast.makeText(this, "Maksimum " + MAX_DOCK + " app", Toast.LENGTH_SHORT).show();
                } else {
                    chosen.add(p);
                }
            });
        });
        d.show();
    }

    // ---- sending ---------------------------------------------------------------------------------

    /** Sends one section (Settings.WALLPAPER, HOME, PRAYER, BISMILLAH) or ALL; the rest of the TV stays as it is. */
    private void send(int section) {
        String h = host();
        if (h.isEmpty()) {
            done("Sambung ke TV dahulu.");
            return;
        }
        Settings s = new Settings();
        s.places = places.isChecked();
        s.wallpaperSeconds = INTERVAL_S[interval.getSelectedItemPosition()];
        s.dockApps = dock;
        s.columns = columns.getSelectedItemPosition() + 4;
        s.networkPill = wifi.isChecked();
        s.reducedMotion = reduced.isChecked();
        s.keepScreenOn = ((Switch) findViewById(R.id.keepOn)).isChecked();
        s.bismillah = bismillah.isChecked();
        Zones.Zone z = Zones.all().get(zone.getSelectedItemPosition());
        s.zone = z.code;
        s.zoneName = z.code.equals("SGR01") ? "Shah Alam" : Zones.shortName(z);
        List<String> on = new ArrayList<>();
        for (int i = 0; i < 5; i++) if (prayers[i].isChecked()) on.add(PRAYER_KEY[i]);
        s.azanPrayers = on;
        s.azanText = azanText.getText().toString().trim();
        s.azanSubtext = azanSubtext.getText().toString().trim();
        s.azanDim = dim.getSelectedItemPosition();
        boolean wallpaper = section == Settings.ALL || section == Settings.WALLPAPER;
        boolean prayer = section == Settings.ALL || section == Settings.PRAYER;
        if (prayer && on.isEmpty()) {
            done("Pilih sekurang-kurangnya satu waktu azan.");
            return;
        }
        String gate = SendGate.check(filled, wallpaper && own.isChecked(), photos.size(), tvHasPhotos);
        if (gate != null) {
            done(gate);
            if (!filled) connect();
            return;
        }
        Settings part = Settings.only(section, s);
        List<String> targets = tvs.selectedIps();
        if (targets.isEmpty()) {
            done("Tanda sekurang-kurangnya satu TV dalam TV SAYA.");
            return;
        }
        List<Uri> sendPhotos = wallpaper && !s.places ? new ArrayList<>(photos) : new ArrayList<>();
        boolean dark = darken.isChecked();
        boolean whole = fit.isChecked();
        Uri aw = prayer ? azanWallpaper : null, a = prayer ? azan : null, as = prayer ? azanSubuh : null;
        boolean azanWhole = ((RadioButton) findViewById(R.id.azanFit)).isChecked();
        setSending(true);
        result.setText("Menyediakan…");
        worker.execute(() -> {
            Sender.Prepared pr = Sender.prepare(this, part, sendPhotos, dark, whole, aw, azanWhole, a, as,
                    step -> runOnUiThread(() -> result.setText(step)));
            Batch batch = new Batch();
            int i = 0;
            for (String ip : targets) {
                TvList.Entry e = tvs.find(ip);
                String label = e != null ? e.name : ip;
                final String prefix = "(" + (++i) + "/" + targets.size() + ") " + label + ": ";
                String line = Sender.sendTo(this, ip, part, pr,
                        step -> runOnUiThread(() -> result.setText(prefix + step)));
                batch.add(label, line);
            }
            pr.cleanup();
            runOnUiThread(() -> {
                setSending(false);
                done(batch.headline());
                if (targets.size() > 1 || !batch.allOk()) showReport("Hantar: " + batch.headline(), batch.details());
                if (batch.allOk()) forgetSent(!sendPhotos.isEmpty(), aw != null || a != null || as != null);
            });
        });
    }

    /** The result shows at the bottom and, since the button may be higher up, as a toast too. */
    private void done(String line) {
        result.setText(line);
        Toast.makeText(this, line, Toast.LENGTH_LONG).show();
    }

    private void setSending(boolean sending) {
        for (int id : new int[]{R.id.send, R.id.sendWallpaper, R.id.sendHome, R.id.sendPrayer, R.id.sendBismillah}) {
            findViewById(id).setEnabled(!sending);
        }
    }

    /** What reached the TV is not sent again next time. */
    private void forgetSent(boolean sentPhotos, boolean sentAzanFiles) {
        if (sentPhotos) {
            photos.clear();
            thumbs.removeAllViews();
            updatePhotoCount();
            tvHasPhotos = true;
        }
        if (sentAzanFiles) {
            azanWallpaper = null;
            azan = null;
            azanSubuh = null;
            ((TextView) findViewById(R.id.azanWallpaperName)).setText("Gambar latar azan: sudah di TV");
            azanName.setText("Azan: sudah di TV");
            azanSubuhName.setText("Azan Subuh: sudah di TV / tidak diubah");
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
