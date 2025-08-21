package com.example.checkinset.model;

/**
 * Repräsentiert einen gesetzten Punkt auf dem Bild.
 */
public class PointModel {
    public float xPercent;  // 0..1 relativ zur Bildbreite
    public float yPercent;  // 0..1 relativ zur Bildhöhe
    public String timestamp;
    public int mark = 0; // Default-Wert 0
    public int color;       // ARGB-Farbe
}
