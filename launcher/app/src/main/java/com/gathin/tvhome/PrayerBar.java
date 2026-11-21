package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhome.PrayerTimes.Next;
import com.gathin.tvhome.PrayerTimes.Prayer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Prayer times on Home: the next prayer in a pill in the top bar ("Zohor 1:10 PM · 7j 2m lagi"), the
 * day's five times as chips under the place's caption, the next one in gold, and the Hijri date under
 * the date. All of it is hidden when the feature is off or there is no JAKIM data for today.
 */
final class PrayerBar {
    private static final int GOLD = 0xFFFFCF7A;

    private final Context c;
    private final TextView pill;
    private final LinearLayout chips;
    private final TextView hijri;
    private final float dp;
    private final Typeface font;

    PrayerBar(Context c, TextView pill, LinearLayout chips, TextView hijri) {
        this.c = c;
        this.pill = pill;
        this.chips = chips;
        this.hijri = hijri;
        this.dp = c.getResources().getDisplayMetrics().density;
        this.font = c.getResources().getFont(R.font.outfit);
    }

    static String countdown(Duration d) {
        long min = Math.max(1, (d.getSeconds() + 59) / 60);
        if (min < 60) return min + " min lagi";
        return (min / 60) + "j" + (min % 60 == 0 ? "" : " " + (min % 60) + "m") + " lagi";
    }

    static String clock(LocalTime t, boolean h24) {
        return DateTimeFormatter.ofPattern(h24 ? "H:mm" : "h:mm a", Locale.US).format(t);
    }

    void update(PrayerTimes t, ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(PrayerTimes.MY).toLocalDate();
        if (t == null || !t.has(today)) {
            pill.setVisibility(View.GONE);
            chips.setVisibility(View.GONE);
            hijri.setVisibility(View.GONE);
            return;
        }
        boolean h24 = DateFormat.is24HourFormat(c);
        Next next = t.next(now);
        if (next != null) {
            String head = "🕌  " + next.prayer.label() + " " + clock(next.time.toLocalTime(), h24) + "  ";
            SpannableStringBuilder sb = new SpannableStringBuilder(head);
            int from = sb.length();
            sb.append("· ").append(countdown(Duration.between(now, next.time)));
            sb.setSpan(new ForegroundColorSpan(GOLD), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pill.setText(sb);
            pill.setVisibility(View.VISIBLE);
        } else {
            pill.setVisibility(View.GONE);
        }
        String h = t.hijri(today);
        hijri.setText(h == null ? "" : h);
        hijri.setVisibility(h == null ? View.GONE : View.VISIBLE);

        chips.removeAllViews();
        for (Prayer p : Prayer.values()) {
            boolean isNext = next != null && next.prayer == p && next.time.toLocalDate().equals(today);
            chips.addView(chip(p.label(), clock(t.at(today, p).toLocalTime(), h24), isNext));
        }
        chips.setVisibility(View.VISIBLE);
    }

    private View chip(String label, String time, boolean next) {
        LinearLayout chip = new LinearLayout(c);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(13 * dp);
        chip.setPadding(pad, 0, pad, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(15 * dp);
        bg.setColor(next ? 0x8C8A6420 : 0x73000000);   // dark enough to read over a bright photo
        bg.setStroke(Math.max(1, Math.round(dp)), next ? 0xE6FFD28C : 0x61FFFFFF);
        chip.setBackground(bg);
        TextView l = text(label.toUpperCase(Locale.ROOT), 11, 0xB3FFFFFF);
        l.setLetterSpacing(0.08f);
        chip.addView(l);
        TextView v = text(time, 12.5f, 0xFFFFFFFF);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.leftMargin = Math.round(7 * dp);
        chip.addView(v, vl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, Math.round(30 * dp));
        cl.rightMargin = Math.round(8 * dp);
        chip.setLayoutParams(cl);
        return chip;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(4, 0, 1, 0x99000000);
        return t;
    }
}
