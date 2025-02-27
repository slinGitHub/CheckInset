package com.example.checkinset.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Repräsentiert ein Bild + Metadaten (Titel, Liste von Punkten).
 */
public class ImageModel {
    public String path;   // z.B. currentPhotoPath
    public String title;  // Überschrift
    public List<com.example.checkinset.model.PointModel> points = new ArrayList<>();
}

