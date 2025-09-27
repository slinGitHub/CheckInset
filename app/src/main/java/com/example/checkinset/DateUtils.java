package com.example.checkinset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    public static String convertDate(String input) {
        try {
            // Eingabeformat (wie dein String aussieht)
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            // Ausgabeformat (wie es aussehen soll)
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd. MMM yy HH:mm", Locale.GERMAN);

            // String -> Date parsen
            Date date = inputFormat.parse(input);

            // Date -> neuer String
            return outputFormat.format(date);

        } catch (ParseException e) {
            e.printStackTrace();
            return input; // falls Parsing fehlschlägt, original zurückgeben
        }
    }

    public static void main(String[] args) {
        String raw = "2025-02-23 18:30:00";
        System.out.println(convertDate(raw)); // 👉 "23. Feb 25 18:30"
    }
}

