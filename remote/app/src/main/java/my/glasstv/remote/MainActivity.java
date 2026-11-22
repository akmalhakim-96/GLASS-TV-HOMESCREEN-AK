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
    private EditText ip, azanText, azanSubtext;
    private RadioButton places, own;
    private Switch darken, fit, wifi, reduced, bismillah;
    private Spinner interval, columns, zone, dim;
    private LinearLayout thumbs, ownBox;
    private final CheckBox[] prayers = new CheckBox[5];
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
        ip = findViewById(R.id.ip);
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

        ip.setText(prefs.getString("ip", ""));
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
        for (int i = 0; i < 5; i++) {
            prayers[i] = new CheckBox(this);
            prayers[i].setText(PRAYER_LABEL[i]);
            prayers[i].setTextColor(0xFFFFFFFF);
            prayers[i].setChecked(true);
            prayerBox.addView(prayers[i]);
        }
        places.setChecked(true);
        ownBox.setVisibility(View.GONE);
        ((android.widget.RadioGroup) findViewById(R.id.wallMode)).setOnCheckedChangeListener(
                (g, id) -> ownBox.setVisibility(id == R.id.own ? View.VISIBLE : View.GONE));

        findViewById(R.id.scan).setOnClickListener(v -> scan());
        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.pickPhotos).setOnClickListener(v -> pick("image/*", true, PICK_PHOTOS));
        findViewById(R.id.pickAzanWallpaper).setOnClickListener(v -> pick("image/*", false, PICK_AZAN_WALLPAPER));
        findViewById(R.id.pickAzan).setOnClickListener(v -> pick("audio/*", false, PICK_AZAN));
        findViewById(R.id.pickAzanSubuh).setOnClickListener(v -> pick("audio/*", false, PICK_AZAN_SUBUH));
        findViewById(R.id.pickDock).setOnClickListener(v -> pickDock());
        findViewById(R.id.setupHome).setOnClickListener(v -> confirmSetupHome());
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

    private String host() {
        return ip.getText().toString().trim();
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
        for (int id : new int[]{R.id.scan, R.id.connect, R.id.setupHome, R.id.send, R.id.sendWallpaper, R.id.sendHome,
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
                } else if (found.size() == 1) {
                    ip.setText(found.get(0));
                    connect();
                } else {
                    String[] items = found.toArray(new String[0]);
                    new AlertDialog.Builder(this).setTitle("Pilih TV").setItems(items, (d, w) -> {
                        ip.setText(items[w]);
                        connect();
                    }).show();
                }
            });
        });
    }

    /** Connects, then fills the form from the TV's current config.json. */
    private void connect() {
        String h = host();
        if (h.isEmpty()) {
            status.setText("Tekan Cari TV atau taip IP TV.");
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

    private void confirmSetupHome() {
        String h = host();
        if (h.isEmpty()) {
            status.setText("Tekan Cari TV atau taip IP TV dahulu.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Pasang Glass TV di TV?")
                .setMessage("Glass TV akan dipasang dan dijadikan Home. Home asal TV dimatikan (tidak dibuang). "
                        + "Jika ada yang tidak kena, Home asal dikembalikan dengan sendiri.\n\n"
                        + "Jika TV bertanya \"Allow USB debugging?\", tanda Always allow dan tekan OK. "
                        + "Proses ini ambil masa ±1 minit.")
                .setPositiveButton("Pasang", (d, w) -> setupHome(h))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void setupHome(String h) {
        findViewById(R.id.setupHome).setEnabled(false);
        status.setText("Bersambung ke " + h + "…");
        worker.execute(() -> {
            String line = HomeSetup.run(this, h, step -> runOnUiThread(() -> status.setText(step)));
            runOnUiThread(() -> {
                findViewById(R.id.setupHome).setEnabled(true);
                status.setText(line);
                if (HomeSetup.lastReport != null) showReport(HomeSetup.lastReport);
                if (line.startsWith("Siap")) {
                    filled = false;   // Glass TV is new on the TV: read its settings again before HANTAR
                    prefs.edit().putString("ip", h).apply();
                    connectQuietly(line);
                }
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
        List<Uri> sendPhotos = wallpaper && !s.places ? new ArrayList<>(photos) : new ArrayList<>();
        boolean dark = darken.isChecked();
        boolean whole = fit.isChecked();
        Uri aw = prayer ? azanWallpaper : null, a = prayer ? azan : null, as = prayer ? azanSubuh : null;
        boolean azanWhole = ((RadioButton) findViewById(R.id.azanFit)).isChecked();
        setSending(true);
        result.setText("Menghantar…");
        worker.execute(() -> {
            String line = Sender.send(this, h, part, sendPhotos, dark, whole, aw, azanWhole, a, as,
                    step -> runOnUiThread(() -> result.setText(step)));
            runOnUiThread(() -> {
                setSending(false);
                done(line);
                if (line.startsWith("Siap")) forgetSent(!sendPhotos.isEmpty(), aw != null || a != null || as != null);
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
