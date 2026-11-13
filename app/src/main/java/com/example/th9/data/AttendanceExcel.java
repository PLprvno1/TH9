package com.example.th9.data;

import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes an actual .xlsx workbook using inline strings, preserving student numbers as text. */
public final class AttendanceExcel {
    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";

    private AttendanceExcel() {}

    public static void write(AttendanceReport report, OutputStream output) throws IOException {
        if (report.tables.isEmpty()) throw new IOException("Chưa có lớp học để xuất Excel");
        for (AttendanceReport.ClassTable table : report.tables) {
            if (table.headers.size() > 16384 || table.rows.size() > 1048572)
                throw new IOException("Bảng vượt giới hạn của Excel. Hãy chọn một khoảng thời gian ngắn hơn.");
        }
        List<String> names = sheetNames(report);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            part(zip, "[Content_Types].xml", TYPES, "Types", xml -> {
                empty(xml, TYPES, "Default", "Extension", "rels", "ContentType", "application/vnd.openxmlformats-package.relationships+xml");
                empty(xml, TYPES, "Default", "Extension", "xml", "ContentType", "application/xml");
                empty(xml, TYPES, "Override", "PartName", "/xl/workbook.xml", "ContentType", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml");
                empty(xml, TYPES, "Override", "PartName", "/xl/styles.xml", "ContentType", "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml");
                for (int i = 0; i < report.tables.size(); i++) {
                    empty(xml, TYPES, "Override", "PartName", "/xl/worksheets/sheet" + (i + 1) + ".xml", "ContentType", "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml");
                }
            });
            part(zip, "_rels/.rels", REL, "Relationships", xml ->
                    empty(xml, REL, "Relationship", "Id", "rId1", "Type", DOC_REL + "/officeDocument", "Target", "xl/workbook.xml"));
            part(zip, "xl/workbook.xml", SHEET, "workbook", xml -> {
                xml.startTag(SHEET, "bookViews");
                empty(xml, SHEET, "workbookView", "activeTab", "0");
                xml.endTag(SHEET, "bookViews");
                xml.startTag(SHEET, "sheets");
                for (int i = 0; i < names.size(); i++) {
                    xml.startTag(SHEET, "sheet").attribute(null, "name", names.get(i))
                            .attribute(null, "sheetId", String.valueOf(i + 1)).attribute(DOC_REL, "id", "rId" + (i + 1)).endTag(SHEET, "sheet");
                }
                xml.endTag(SHEET, "sheets");
                xml.startTag(SHEET, "definedNames");
                for (int i = 0; i < names.size(); i++) {
                    String quotedName = "'" + names.get(i).replace("'", "''") + "'";
                    xml.startTag(SHEET, "definedName").attribute(null, "name", "_xlnm.Print_Titles").attribute(null, "localSheetId", String.valueOf(i))
                            .text(quotedName + "!$1:$4," + quotedName + "!$A:$C").endTag(SHEET, "definedName");
                }
                xml.endTag(SHEET, "definedNames");
            });
            part(zip, "xl/_rels/workbook.xml.rels", REL, "Relationships", xml -> {
                for (int i = 0; i < names.size(); i++) {
                    empty(xml, REL, "Relationship", "Id", "rId" + (i + 1), "Type", DOC_REL + "/worksheet", "Target", "worksheets/sheet" + (i + 1) + ".xml");
                }
                empty(xml, REL, "Relationship", "Id", "rIdStyles", "Type", DOC_REL + "/styles", "Target", "styles.xml");
            });
            part(zip, "xl/styles.xml", SHEET, "styleSheet", AttendanceExcel::styles);
            for (int i = 0; i < report.tables.size(); i++) {
                AttendanceReport.ClassTable table = report.tables.get(i);
                part(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", SHEET, "worksheet", xml -> worksheet(xml, report, table));
            }
            zip.finish();
        }
    }

    private interface PartWriter { void write(XmlSerializer xml) throws IOException; }

    private static void part(ZipOutputStream zip, String path, String namespace, String root, PartWriter writer) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        XmlSerializer xml = Xml.newSerializer();
        xml.setOutput(zip, "UTF-8");
        xml.startDocument("UTF-8", true);
        xml.setPrefix("", namespace);
        if ("workbook".equals(root)) xml.setPrefix("r", DOC_REL);
        xml.startTag(namespace, root);
        writer.write(xml);
        xml.endTag(namespace, root);
        xml.endDocument();
        xml.flush();
        zip.closeEntry();
    }

    private static void empty(XmlSerializer xml, String namespace, String tag, String... attributes) throws IOException {
        xml.startTag(namespace, tag);
        for (int i = 0; i < attributes.length; i += 2) xml.attribute(null, attributes[i], attributes[i + 1]);
        xml.endTag(namespace, tag);
    }

    private static void styles(XmlSerializer xml) throws IOException {
        xml.startTag(SHEET, "fonts").attribute(null, "count", "5");
        font(xml, "FF0F172A", false, "11");
        font(xml, "FFFFFFFF", true, "11");
        font(xml, "FF16A34A", true, "11");
        font(xml, "FFD97706", true, "11");
        font(xml, "FF1E3A8A", true, "14");
        xml.endTag(SHEET, "fonts");
        xml.startTag(SHEET, "fills").attribute(null, "count", "4");
        for (String pattern : new String[]{"none", "gray125"}) {
            xml.startTag(SHEET, "fill"); empty(xml, SHEET, "patternFill", "patternType", pattern); xml.endTag(SHEET, "fill");
        }
        for (String color : new String[]{"FF1E3A8A", "FFF1F5F9"}) {
            xml.startTag(SHEET, "fill").startTag(SHEET, "patternFill").attribute(null, "patternType", "solid");
            empty(xml, SHEET, "fgColor", "rgb", color); empty(xml, SHEET, "bgColor", "indexed", "64");
            xml.endTag(SHEET, "patternFill").endTag(SHEET, "fill");
        }
        xml.endTag(SHEET, "fills");
        xml.startTag(SHEET, "borders").attribute(null, "count", "2");
        for (int i = 0; i < 2; i++) {
            xml.startTag(SHEET, "border");
            for (String side : new String[]{"left", "right", "top", "bottom", "diagonal"}) {
                xml.startTag(SHEET, side);
                if (i == 1 && !side.equals("diagonal")) {
                    xml.attribute(null, "style", "thin"); empty(xml, SHEET, "color", "rgb", "FFE2E8F0");
                }
                xml.endTag(SHEET, side);
            }
            xml.endTag(SHEET, "border");
        }
        xml.endTag(SHEET, "borders");
        xml.startTag(SHEET, "cellStyleXfs").attribute(null, "count", "1");
        empty(xml, SHEET, "xf", "numFmtId", "0", "fontId", "0", "fillId", "0", "borderId", "0");
        xml.endTag(SHEET, "cellStyleXfs");
        xml.startTag(SHEET, "cellXfs").attribute(null, "count", "12");
        xf(xml, 0, 0, 0, "left", false);
        xf(xml, 4, 0, 0, "left", true);
        xf(xml, 0, 0, 0, "left", true);
        xf(xml, 1, 2, 1, "center", true);
        xf(xml, 0, 0, 1, "center", false);
        xf(xml, 0, 3, 1, "center", false);
        xf(xml, 0, 0, 1, "left", false);
        xf(xml, 0, 3, 1, "left", false);
        xf(xml, 2, 0, 1, "center", false);
        xf(xml, 2, 3, 1, "center", false);
        xf(xml, 3, 0, 1, "center", false);
        xf(xml, 3, 3, 1, "center", false);
        xml.endTag(SHEET, "cellXfs");
        xml.startTag(SHEET, "cellStyles").attribute(null, "count", "1");
        empty(xml, SHEET, "cellStyle", "name", "Normal", "xfId", "0", "builtinId", "0");
        xml.endTag(SHEET, "cellStyles");
    }

    private static void font(XmlSerializer xml, String color, boolean bold, String size) throws IOException {
        xml.startTag(SHEET, "font");
        if (bold) empty(xml, SHEET, "b");
        empty(xml, SHEET, "sz", "val", size); empty(xml, SHEET, "color", "rgb", color);
        empty(xml, SHEET, "name", "val", "Calibri");
        xml.endTag(SHEET, "font");
    }

    private static void xf(XmlSerializer xml, int font, int fill, int border, String alignment, boolean wrap) throws IOException {
        xml.startTag(SHEET, "xf").attribute(null, "numFmtId", "0").attribute(null, "fontId", String.valueOf(font))
                .attribute(null, "fillId", String.valueOf(fill)).attribute(null, "borderId", String.valueOf(border))
                .attribute(null, "xfId", "0").attribute(null, "applyFont", "1").attribute(null, "applyFill", "1")
                .attribute(null, "applyBorder", "1").attribute(null, "applyAlignment", "1");
        empty(xml, SHEET, "alignment", "horizontal", alignment, "vertical", "center", "wrapText", wrap ? "1" : "0");
        xml.endTag(SHEET, "xf");
    }

    private static void worksheet(XmlSerializer xml, AttendanceReport report, AttendanceReport.ClassTable table) throws IOException {
        String lastColumn = column(table.headers.size() - 1);
        int lastRow = table.rows.size() + 4;
        xml.startTag(SHEET, "sheetPr"); empty(xml, SHEET, "pageSetUpPr", "fitToPage", "1"); xml.endTag(SHEET, "sheetPr");
        empty(xml, SHEET, "dimension", "ref", "A1:" + lastColumn + lastRow);
        xml.startTag(SHEET, "sheetViews").startTag(SHEET, "sheetView").attribute(null, "workbookViewId", "0");
        empty(xml, SHEET, "pane", "xSplit", table.sessionCount > 0 ? "3" : "0", "ySplit", "4",
                "topLeftCell", table.sessionCount > 0 ? "D5" : "A5", "activePane", table.sessionCount > 0 ? "bottomRight" : "bottomLeft", "state", "frozen");
        xml.endTag(SHEET, "sheetView").endTag(SHEET, "sheetViews");
        empty(xml, SHEET, "sheetFormatPr", "defaultRowHeight", "26");
        xml.startTag(SHEET, "cols");
        for (int i = 0; i < table.headers.size(); i++) {
            empty(xml, SHEET, "col", "min", String.valueOf(i + 1), "max", String.valueOf(i + 1),
                    "width", i == 0 ? "7" : i == 1 ? "19" : i == 2 ? "32" : "17", "customWidth", "1");
        }
        xml.endTag(SHEET, "cols");
        xml.startTag(SHEET, "sheetData");
        metadataRow(xml, 1, table.title, 1, "26");
        metadataRow(xml, 2, report.period, 2, "24");
        metadataRow(xml, 3, report.legend, 2, "30");
        xml.startTag(SHEET, "row").attribute(null, "r", "4").attribute(null, "ht", "48").attribute(null, "customHeight", "1");
        for (int i = 0; i < table.headers.size(); i++) cell(xml, column(i) + "4", table.headers.get(i), 3, false);
        xml.endTag(SHEET, "row");
        for (int row = 0; row < table.rows.size(); row++) {
            int number = row + 5, alternate = row % 2;
            xml.startTag(SHEET, "row").attribute(null, "r", String.valueOf(number));
            List<String> cells = table.rows.get(row);
            for (int col = 0; col < cells.size(); col++) {
                String value = cells.get(col);
                int style = col == 1 || col == 2 ? 6 + alternate : col >= 3 && value.equals("x") ? 8 + alternate
                        : col >= 3 && value.equals("t") ? 10 + alternate : 4 + alternate;
                cell(xml, column(col) + number, value, style, col == 0);
            }
            xml.endTag(SHEET, "row");
        }
        xml.endTag(SHEET, "sheetData");
        empty(xml, SHEET, "autoFilter", "ref", "A4:" + lastColumn + lastRow);
        xml.startTag(SHEET, "mergeCells").attribute(null, "count", "3");
        for (int row = 1; row <= 3; row++) empty(xml, SHEET, "mergeCell", "ref", "A" + row + ":" + lastColumn + row);
        xml.endTag(SHEET, "mergeCells");
        empty(xml, SHEET, "printOptions", "horizontalCentered", "1");
        empty(xml, SHEET, "pageMargins", "left", "0.25", "right", "0.25", "top", "0.5", "bottom", "0.5", "header", "0.2", "footer", "0.2");
        empty(xml, SHEET, "pageSetup", "paperSize", "9", "orientation", "landscape", "fitToWidth", "1", "fitToHeight", "0");
    }

    private static void metadataRow(XmlSerializer xml, int row, String value, int style, String height) throws IOException {
        xml.startTag(SHEET, "row").attribute(null, "r", String.valueOf(row)).attribute(null, "ht", height).attribute(null, "customHeight", "1");
        cell(xml, "A" + row, value, style, false);
        xml.endTag(SHEET, "row");
    }

    private static void cell(XmlSerializer xml, String address, String value, int style, boolean numeric) throws IOException {
        xml.startTag(SHEET, "c").attribute(null, "r", address).attribute(null, "s", String.valueOf(style)).attribute(null, "t", numeric ? "n" : "inlineStr");
        if (numeric) xml.startTag(SHEET, "v").text(value).endTag(SHEET, "v");
        else {
            xml.startTag(SHEET, "is").startTag(SHEET, "t").attribute("http://www.w3.org/XML/1998/namespace", "space", "preserve")
                    .text(cleanXml(value)).endTag(SHEET, "t").endTag(SHEET, "is");
        }
        xml.endTag(SHEET, "c");
    }

    private static String cleanXml(String value) {
        StringBuilder text = new StringBuilder();
        value.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF).forEach(text::appendCodePoint);
        return text.toString();
    }

    private static String column(int index) {
        StringBuilder result = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) result.append((char) ('A' + (n - 1) % 26));
        return result.reverse().toString();
    }

    private static List<String> sheetNames(AttendanceReport report) {
        List<String> names = new ArrayList<>(); Set<String> used = new HashSet<>();
        for (AttendanceReport.ClassTable table : report.tables) {
            String base = cleanXml(table.title).replaceAll("[\\\\/?:*\\[\\]]", " ").trim().replaceAll("^'+|'+$", "").trim();
            if (base.isEmpty()) base = "Lớp học";
            String name = sheetPrefix(base, 31); int duplicate = 1;
            while (!used.add(name.toLowerCase(Locale.ROOT))) {
                String suffix = " (" + (++duplicate) + ")";
                name = sheetPrefix(base, 31 - suffix.length()) + suffix;
            }
            names.add(name);
        }
        return names;
    }

    private static String sheetPrefix(String name, int limit) {
        int end = Math.min(limit, name.length());
        if (end > 0 && Character.isHighSurrogate(name.charAt(end - 1))) end--;
        String result = name.substring(0, end).replaceAll("'+$", "").trim();
        return result.isEmpty() ? "Lớp học" : result;
    }
}
