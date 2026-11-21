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
    private static final int PICK_PHOTOS = 1, PICK_AZAN = 2, PICK_AZAN_SUBUH = 3;
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
    private Switch darken, wifi, reduced, bismillah;
    private Spinner interval, columns, zone, dim;
    private LinearLayout thumbs, ownBox;
    private final CheckBox[] prayers = new CheckBox[5];
    private Button send;

    private final List<Uri> photos = new ArrayList<>();
    private List<String> dock;   // null: leave the TV's dock alone
    private Uri azan, azanSubuh;
    /** The form shows the TV's own settings (read on a successful connect); HANTAR waits for it. */
    private boolean filled;
    private boolean tvHasPhotos;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("remote", MODE_PRIVATE);
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
        findViewById(R.id.pickAzan).setOnClickListener(v -> pick("audio/*", false, PICK_AZAN));
        findViewById(R.id.pickAzanSubuh).setOnClickListener(v -> pick("audio/*", false, PICK_AZAN_SUBUH));
        findViewById(R.id.pickDock).setOnClickListener(v -> pickDock());
        send.setOnClickListener(v -> send());

        restore(b);
        // With a remembered TV, read its settings straight away so the form never starts from guesses.
        if (!host().isEmpty()) connect();
    }

    /** Picks survive the phone closing the app while the photo or file picker is open. */
    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelableArrayList("photos", new ArrayList<>(photos));
        out.putParcelable("azan", azan);
        out.putParcelable("azanSubuh", azanSubuh);
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
                JSONObject cfg = ConfigMerge.read(tv.shell(TvCommands.readConfig()));
                int pictures = 0;
                try {
                    pictures = Integer.parseInt(tv.shell(TvCommands.countWallpapers()).trim());
                } catch (NumberFormatException ignored) {
                }
                final int onTv = pictures;
                prefs.edit().putString("ip", h).apply();
                runOnUiThread(() -> {
                    status.setText("Bersambung ✓ " + h);
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

    private void send() {
        String h = host();
        if (h.isEmpty()) {
            result.setText("Sambung ke TV dahulu.");
            return;
        }
        Settings s = new Settings();
        s.places = places.isChecked();
        s.wallpaperSeconds = INTERVAL_S[interval.getSelectedItemPosition()];
        s.dockApps = dock;
        s.columns = columns.getSelectedItemPosition() + 4;
        s.networkPill = wifi.isChecked();
        s.reducedMotion = reduced.isChecked();
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
        if (on.isEmpty()) {
            result.setText("Pilih sekurang-kurangnya satu waktu azan.");
            return;
        }
        String gate = SendGate.check(filled, own.isChecked(), photos.size(), tvHasPhotos);
        if (gate != null) {
            result.setText(gate);
            if (!filled) connect();
            return;
        }
        List<Uri> sendPhotos = s.places ? new ArrayList<>() : new ArrayList<>(photos);
        boolean dark = darken.isChecked();
        Uri a = azan, as = azanSubuh;
        send.setEnabled(false);
        result.setText("Menghantar…");
        worker.execute(() -> {
            String line = Sender.send(this, h, s, sendPhotos, dark, a, as,
                    step -> runOnUiThread(() -> result.setText(step)));
            runOnUiThread(() -> {
                result.setText(line);
                send.setEnabled(true);
            });
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
