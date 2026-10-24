package com.omran.notizapp.ui.util

import android.content.res.Resources
import com.omran.notizapp.R

/**
 * Translates recognized internal validation errors when they are shown in the UI.
 * Repositories intentionally keep their existing messages and behavior.
 * Unexpected errors are shown as the localized generic error, not a raw German message.
 */
fun localizedTechnicalError(resources: Resources, error: Throwable): String {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 5) {
        val message = current.message
        if (message != null) {
            translateKnownTechnicalMessage(resources, message)?.let { return it }
        }
        current = current.cause
        depth++
    }
    return resources.getString(R.string.error_unknown)
}

private fun translateKnownTechnicalMessage(resources: Resources, message: String): String? {
    val exact = when (message) {
        "Ungültiger Audiodateiname." -> R.string.tech_invalid_audio_name
        "Ungültiger Bilddateiname." -> R.string.tech_invalid_image_name
        "Die Aufnahme ist leer oder fehlt." -> R.string.tech_recording_missing
        "Ungültiger Aufnahmeordner." -> R.string.tech_recording_dir_invalid
        "Audioordner konnte nicht erstellt werden." -> R.string.tech_audio_dir_fail
        "Audiodatei konnte nicht dauerhaft abgelegt werden." -> R.string.tech_audio_save_fail
        "Die Notiz ist nicht mehr verfügbar." -> R.string.tech_note_not_found
        "Notiz ist nicht mehr verfügbar." -> R.string.tech_note_not_found_short
        "Aufnahme gehört zu einer anderen Notiz." -> R.string.tech_audio_other_note
        "Das Bild gehört zu einer anderen Notiz." -> R.string.tech_image_other_note
        "Es läuft bereits eine Aufnahme." -> R.string.tech_recording_running
        "Keine Aufnahme aktiv." -> R.string.tech_recording_not_active
        "Keine Audiodatei vorhanden." -> R.string.tech_recording_file_missing
        "Die Aufnahme ist leer." -> R.string.tech_recording_empty
        "Bildformat konnte nicht ermittelt werden." -> R.string.tech_image_mime_unknown
        "HEIC-Bilder werden auf diesem Android-Gerät nicht unterstützt." -> R.string.tech_image_heic_unsupported
        "AVIF-Bilder werden auf diesem Android-Gerät nicht unterstützt." -> R.string.tech_image_avif_unsupported
        "Bildordner konnte nicht erstellt werden." -> R.string.tech_image_dir_fail
        "Bild konnte nicht geöffnet werden." -> R.string.tech_image_open_fail
        "Bild ist größer als 25 MB." -> R.string.tech_image_too_large
        "Bilddatei ist leer." -> R.string.tech_image_empty
        "Bilddatei ist beschädigt oder nicht lesbar." -> R.string.tech_image_corrupt
        "Bild konnte nicht dauerhaft gespeichert werden." -> R.string.tech_image_save_fail
        "Erstelltes Backup ist unvollständig." -> R.string.tech_backup_created_incomplete
        "Backup-Ziel konnte nicht geöffnet werden." -> R.string.tech_backup_destination_fail
        "Backup ist zu groß (maximal 2 GiB)." -> R.string.tech_backup_too_large
        "Die ausgewählte Datei kann nicht geöffnet werden." -> R.string.tech_backup_source_fail
        "Zieldatei konnte nicht erstellt werden." -> R.string.tech_backup_file_create_fail
        "Ungültige oder zyklische Kategoriehierarchie." -> R.string.tech_backup_category_cycle
        "Notiz ohne gültige Kategorie." -> R.string.tech_backup_note_without_category
        "Notizimport fehlgeschlagen." -> R.string.tech_backup_note_import_fail
        "Audio ohne gültige Notiz." -> R.string.tech_backup_audio_without_note
        "Audiodatei fehlt." -> R.string.tech_backup_audio_missing
        "Audiodateigröße fehlt." -> R.string.tech_backup_audio_size_missing
        "Bild ohne gültige Notiz." -> R.string.tech_backup_image_without_note
        "Bilddatei fehlt." -> R.string.tech_backup_image_missing
        "Bilddateigröße fehlt." -> R.string.tech_backup_image_size_missing
        "Ungültige oder doppelte Einträge im Backup." -> R.string.tech_backup_duplicate_entries
        "Backup enthält unerwartete Dateien." -> R.string.tech_backup_unexpected_files
        "Unbekanntes Backup-Format." -> R.string.tech_backup_unknown_format
        "Diese Backup-Version wird nicht unterstützt (erwartet DB v5)." -> R.string.tech_backup_version_unsupported
        "Ungültiges Backup-Dateiverzeichnis." -> R.string.tech_backup_dir_invalid
        "Dateien fehlen oder sind nicht im Manifest aufgeführt." -> R.string.tech_backup_manifest_missing
        "Metadatenlänge stimmt nicht." -> R.string.tech_backup_metadata_length
        "Ungültige Kategorien im Backup." -> R.string.tech_backup_categories_invalid
        "Ungültige Notizen im Backup." -> R.string.tech_backup_notes_invalid
        "Ungültige oder doppelte Audiodatei." -> R.string.tech_backup_audio_invalid
        "Ungültige oder doppelte Bilddatei." -> R.string.tech_backup_image_invalid
        "Backup enthält ungültige Verknüpfungen." -> R.string.tech_backup_links_invalid
        "Medien stimmen nicht mit den Datenbankeinträgen überein." -> R.string.tech_backup_media_mismatch
        "Ungültige Mediendateigröße." -> R.string.tech_backup_media_size_invalid
        "Backup-Inhalt zu groß." -> R.string.tech_backup_contents_too_large
        "metadata.json oder manifest.json fehlt." -> R.string.tech_backup_json_missing
        "Backup-Metadaten zu groß." -> R.string.tech_backup_metadata_too_large
        else -> null
    }
    if (exact != null) return resources.getString(exact)

    return when {
        message.startsWith("Dieses Bildformat wird noch nicht unterstützt (") && message.endsWith(").") -> {
            val detail = message.removePrefix("Dieses Bildformat wird noch nicht unterstützt (").removeSuffix(").")
            resources.getString(R.string.tech_image_mime_unsupported, detail)
        }
        message.startsWith("Ungültiger Dateiname im Datenbestand: ") -> {
            val detail = message.removePrefix("Ungültiger Dateiname im Datenbestand: ")
            resources.getString(R.string.tech_backup_file_name_invalid, detail)
        }
        message.startsWith("Doppelte Datei in der Datenbank: ") -> {
            val detail = message.removePrefix("Doppelte Datei in der Datenbank: ")
            resources.getString(R.string.tech_backup_duplicate_file, detail)
        }
        message.startsWith("Anhang fehlt oder ist nicht lesbar: ") -> {
            val detail = message.removePrefix("Anhang fehlt oder ist nicht lesbar: ")
            resources.getString(R.string.tech_backup_attachment_unreadable, detail)
        }
        message.startsWith("Leerer Anhang: ") -> {
            val detail = message.removePrefix("Leerer Anhang: ")
            resources.getString(R.string.tech_backup_attachment_empty, detail)
        }
        message.startsWith("Nicht unterstützter Datentyp in ") -> {
            val detail = message.removePrefix("Nicht unterstützter Datentyp in ")
            resources.getString(R.string.tech_backup_data_type_unsupported, detail)
        }
        message.startsWith("Fehlender ZIP-Eintrag: ") -> {
            val detail = message.removePrefix("Fehlender ZIP-Eintrag: ")
            resources.getString(R.string.tech_backup_zip_entry_missing, detail)
        }
        message.startsWith("Dateigröße hat sich beim Import geändert: ") -> {
            val detail = message.removePrefix("Dateigröße hat sich beim Import geändert: ")
            resources.getString(R.string.tech_backup_size_changed, detail)
        }
        message.startsWith("Anhang ist größer als angegeben: ") -> {
            val detail = message.removePrefix("Anhang ist größer als angegeben: ")
            resources.getString(R.string.tech_backup_attachment_too_large, detail)
        }
        message.startsWith("Falsche Größe: ") -> {
            val detail = message.removePrefix("Falsche Größe: ")
            resources.getString(R.string.tech_backup_size_wrong, detail)
        }
        message.startsWith("Prüfsumme falsch: ") -> {
            val detail = message.removePrefix("Prüfsumme falsch: ")
            resources.getString(R.string.tech_backup_checksum_wrong, detail)
        }
        message.startsWith("Zahl fehlt: ") -> {
            val detail = message.removePrefix("Zahl fehlt: ")
            resources.getString(R.string.tech_backup_number_missing, detail)
        }
        message.startsWith("Spalte fehlt: ") -> {
            val detail = message.removePrefix("Spalte fehlt: ")
            resources.getString(R.string.tech_backup_column_missing, detail)
        }
        else -> null
    }
}
