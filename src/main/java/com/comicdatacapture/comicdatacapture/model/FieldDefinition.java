package com.comicdatacapture.comicdatacapture.model;

import java.util.List;

/**
 * Defines a single custom capture field configured by the operator.
 *
 *   label   — display name shown in the UI       (e.g. "Series Name")
 *   dbKey   — JSONB key written to entry.data    (e.g. "series_name")
 *             Always auto-generated from label; never set manually.
 *   fieldId — UI identity key for controller map lookups; equals dbKey.
 */
public class FieldDefinition {

    public enum FieldType {
        TEXT, NUMBER, DATE, YEAR, CHECKBOX, DROPDOWN
    }

    private String fieldId;
    private String label;
    private String dbKey;
    private FieldType type;
    private boolean required;
    private List<String> dropdownOptions;

    public FieldDefinition() {}

    public FieldDefinition(String label, FieldType type, boolean required) {
        this.label    = label;
        this.dbKey    = toDbKey(label);
        this.fieldId  = this.dbKey;
        this.type     = type;
        this.required = required;
    }

    /**
     * Converts a human-readable label to a safe JSONB key.
     *
     * "Series Name"  -> "series_name"
     * "Release Date" -> "release_date"
     * "Variant?"     -> "variant"
     * "1st Edition"  -> "_1st_edition"
     */
    public static String toDbKey(String label) {
        if (label == null || label.isBlank()) return "field";
        String key = label.trim()
                .toLowerCase()
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (!key.isEmpty() && Character.isDigit(key.charAt(0))) key = "_" + key;
        return key.isEmpty() ? "field" : key;
    }

    public String getFieldId()  { return fieldId; }
    public void   setFieldId(String v) { this.fieldId = v; }

    public String getLabel()    { return label; }
    public void   setLabel(String label) {
        this.label   = label;
        this.dbKey   = toDbKey(label);
        this.fieldId = this.dbKey;
    }

    public String    getDbKey()   { return dbKey; }

    public FieldType getType()    { return type; }
    public void      setType(FieldType type) { this.type = type; }

    public boolean   isRequired() { return required; }
    public void      setRequired(boolean required) { this.required = required; }

    public List<String> getDropdownOptions() { return dropdownOptions; }
    public void setDropdownOptions(List<String> opts) { this.dropdownOptions = opts; }

    @Override
    public String toString() { return label + " (" + type + ") -> " + dbKey; }
}
