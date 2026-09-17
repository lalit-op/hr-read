package com.example.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates an authentic, multi-worksheet Microsoft Excel (.xlsx) OpenXML spreadsheet
 * for CGC (Chandigarh Group of Colleges) academic performance, departmental metrics,
 * fee collection ledger, and grading scales.
 */
object SampleXlsxGenerator {

    const val SAMPLE_FILE_NAME = "CGC.xlsx"

    fun generateCgcWorkbook(targetFile: File, context: Context? = null): File {
        targetFile.parentFile?.mkdirs()

        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

                // 2. _rels/.rels
                writeZipEntry(zos, "_rels/.rels", buildRootRelsXml())

                // 3. xl/workbook.xml
                writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml())

                // 4. xl/_rels/workbook.xml.rels
                writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

                // 5. xl/styles.xml
                writeZipEntry(zos, "xl/styles.xml", buildStylesXml())

                // 6. xl/sharedStrings.xml
                val sharedStrings = buildSharedStringsList()
                writeZipEntry(zos, "xl/sharedStrings.xml", buildSharedStringsXml(sharedStrings))

                val strMap = sharedStrings.mapIndexed { idx, str -> str to idx }.toMap()

                // 7. xl/worksheets/sheet1.xml (Student Records)
                writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheet1Xml(strMap))

                // 8. xl/worksheets/sheet2.xml (Department Summary)
                writeZipEntry(zos, "xl/worksheets/sheet2.xml", buildSheet2Xml(strMap))

                // 9. xl/worksheets/sheet3.xml (Fee Status)
                writeZipEntry(zos, "xl/worksheets/sheet3.xml", buildSheet3Xml(strMap))

                // 10. xl/worksheets/sheet4.xml (Grading Scale)
                writeZipEntry(zos, "xl/worksheets/sheet4.xml", buildSheet4Xml(strMap))
            }
        }

        return targetFile
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val writer = OutputStreamWriter(zos, StandardCharsets.UTF_8)
        writer.write(content.trimIndent())
        writer.flush()
        zos.closeEntry()
    }

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
        </Types>
    """.trimIndent()

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildWorkbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
            <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
            <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
            <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildWorkbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <bookViews>
                <workbookView xWindow="0" yWindow="0" windowWidth="24000" windowHeight="14000"/>
            </bookViews>
            <sheets>
                <sheet name="Student Records" sheetId="1" r:id="rId1"/>
                <sheet name="Department Summary" sheetId="2" r:id="rId2"/>
                <sheet name="Fee Status" sheetId="3" r:id="rId3"/>
                <sheet name="Grading Scale" sheetId="4" r:id="rId4"/>
            </sheets>
        </workbook>
    """.trimIndent()

    private fun buildStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <fonts count="6">
                <font><sz val="11"/><color rgb="FF1F2328"/><name val="Segoe UI"/></font>
                <font><b/><sz val="11"/><color rgb="FF1F2328"/><name val="Segoe UI"/></font>
                <font><b/><sz val="14"/><color rgb="FFFFFFFF"/><name val="Segoe UI"/></font>
                <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Segoe UI"/></font>
                <font><b/><sz val="11"/><color rgb="FF1B5E20"/><name val="Segoe UI"/></font>
                <font><b/><sz val="11"/><color rgb="FFC62828"/><name val="Segoe UI"/></font>
            </fonts>
            <fills count="8">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF1B5E20"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF2E7D32"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFF1F8E9"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFE8F5E9"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFEBEE"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFF8E1"/></patternFill></fill>
            </fills>
            <borders count="3">
                <border/>
                <border>
                    <left style="thin"><color rgb="FFCFD8DC"/></left>
                    <right style="thin"><color rgb="FFCFD8DC"/></right>
                    <top style="thin"><color rgb="FFCFD8DC"/></top>
                    <bottom style="thin"><color rgb="FFCFD8DC"/></bottom>
                </border>
                <border>
                    <left style="thin"><color rgb="FFB0BEC5"/></left>
                    <right style="thin"><color rgb="FFB0BEC5"/></right>
                    <top style="thin"><color rgb="FFB0BEC5"/></top>
                    <bottom style="medium"><color rgb="FF78909C"/></bottom>
                </border>
            </borders>
            <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
            </cellStyleXfs>
            <cellXfs count="14">
                <!-- 0: Default standard cell -->
                <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
                <!-- 1: Big Title Banner (CGC Dark Green, White bold) -->
                <xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
                <!-- 2: Table Column Header (Medium Green, White bold) -->
                <xf numFmtId="0" fontId="3" fillId="3" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
                <!-- 3: Data Cell Left -->
                <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1">
                    <alignment horizontal="left" vertical="center"/>
                </xf>
                <!-- 4: Data Cell Center -->
                <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
                <!-- 5: Data Cell Right -->
                <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1">
                    <alignment horizontal="right" vertical="center"/>
                </xf>
                <!-- 6: Bold Center -->
                <xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
                <!-- 7: Zebra Left -->
                <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1">
                    <alignment horizontal="left" vertical="center"/>
                </xf>
                <!-- 8: Zebra Center -->
                <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
                <!-- 9: Zebra Right -->
                <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1">
                    <alignment horizontal="right" vertical="center"/>
                </xf>
                <!-- 10: Summary Card Label -->
                <xf numFmtId="0" fontId="1" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
                    <alignment horizontal="left" vertical="center"/>
                </xf>
                <!-- 11: Summary Card Value -->
                <xf numFmtId="0" fontId="1" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
                    <alignment horizontal="right" vertical="center"/>
                </xf>
                <!-- 12: Status Distinction (Green text, light gold fill) -->
                <xf numFmtId="0" fontId="4" fillId="7" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
                <!-- 13: Status Pending (Red text, light red fill) -->
                <xf numFmtId="0" fontId="5" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
                    <alignment horizontal="center" vertical="center"/>
                </xf>
            </cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun buildSharedStringsList(): List<String> {
        return listOf(
            // Titles & Headers
            "CHANDIGARH GROUP OF COLLEGES (CGC)",
            "Department of Computer Science & Engineering - Semester IV Consolidated Grade Sheet",
            "Roll No", "Student Name", "Section", "DSA (CS401)", "OS (CS402)", "DBMS (CS403)", "Maths (MA401)",
            "Total (400)", "Percentage", "SGPA", "Status",
            // Statuses
            "PASS - DISTINCTION", "PASS - FIRST CLASS", "PASS", "FAIL - BACKLOG",
            // Students
            "Aarav Sharma", "Ananya Verma", "Rohan Patel", "Priya Singh", "Ishaan Gupta",
            "Simran Kaur", "Vikram Malhotra", "Meera Joshi", "Aditya Rao", "Sneha Nair",
            "Kavya Patel", "Harpreet Singh", "Pooja Reddy", "Nikhil Kumar", "Divya Menon",
            "Arjun Kapoor", "Tanvi Bhatia", "Manish Tiwari", "Rhea Sengupta", "Kabir Bedi",
            "CSE-A", "CSE-B", "CSE-C",
            // Sheet 2 Strings
            "CGC CSE Department - Semester Performance Metrics & KPI Summary",
            "Metric", "Count / Value",
            "Total Enrolled Students", "Total Appeared", "Total Passed", "Distinction (>75%)",
            "First Division (60-74%)", "Department Pass Rate", "Department Average SGPA", "Highest SGPA",
            "94.8%", "7.96", "9.85",
            "Subject Code", "Subject Name", "Faculty In-Charge", "Avg Attendance", "Course Pass %",
            "CS401", "Data Structures & Algorithms", "Dr. Harpreet Singh", "89.4%", "95.2%",
            "CS402", "Operating Systems", "Prof. Neha Sharma", "86.1%", "91.8%",
            "CS403", "Database Management Systems", "Dr. Rajesh Kumar", "91.5%", "97.5%",
            "MA401", "Discrete Mathematics", "Prof. Amit Verma", "84.2%", "88.6%",
            // Sheet 3 Strings
            "CGC Academic Year 2025-2026 Tuition Fee & Dues Ledger",
            "Annual Fee", "Paid Amount", "Balance Due", "Scholarship", "Payment Status", "Receipt No",
            "PAID", "PARTIAL", "PENDING", "MERIT 20%", "SPORTS 15%", "NONE",
            // Sheet 4 Strings
            "CGC Academic Grading System & Credit Regulations",
            "Grade", "Marks Range", "Grade Point", "Performance Level", "Classification",
            "O", "90 - 100", "10.0", "Outstanding", "Distinction",
            "A+", "80 - 89", "9.0", "Excellent", "Distinction",
            "A", "70 - 79", "8.0", "Very Good", "First Class",
            "B+", "60 - 69", "7.0", "Good", "First Class",
            "B", "50 - 59", "6.0", "Above Average", "Second Class",
            "C", "40 - 49", "5.0", "Average", "Pass Class",
            "P", "35 - 39", "4.0", "Pass", "Pass",
            "F", "Below 35", "0.0", "Fail", "Re-appear"
        )
    }

    private fun buildSharedStringsXml(strings: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
        for (s in strings) {
            val escaped = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            sb.append("<si><t>").append(escaped).append("</t></si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun buildSheet1Xml(strMap: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        // Column widths
        sb.append("""<cols>""")
        sb.append("""<col min="1" max="1" width="14" customWidth="1"/>""") // Roll No
        sb.append("""<col min="2" max="2" width="22" customWidth="1"/>""") // Name
        sb.append("""<col min="3" max="3" width="10" customWidth="1"/>""") // Section
        sb.append("""<col min="4" max="4" width="14" customWidth="1"/>""") // DSA
        sb.append("""<col min="5" max="5" width="14" customWidth="1"/>""") // OS
        sb.append("""<col min="6" max="6" width="14" customWidth="1"/>""") // DBMS
        sb.append("""<col min="7" max="7" width="14" customWidth="1"/>""") // Maths
        sb.append("""<col min="8" max="8" width="13" customWidth="1"/>""") // Total
        sb.append("""<col min="9" max="9" width="14" customWidth="1"/>""") // %
        sb.append("""<col min="10" max="10" width="10" customWidth="1"/>""") // SGPA
        sb.append("""<col min="11" max="11" width="24" customWidth="1"/>""") // Status
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        // Row 1: Banner Title
        val titleIdx = strMap["CHANDIGARH GROUP OF COLLEGES (CGC)"] ?: 0
        sb.append("""<row r="1" ht="36" customHeight="1">""")
        sb.append("""<c r="A1" s="1" t="s"><v>$titleIdx</v></c>""")
        sb.append("""</row>""")

        // Row 2: Subtitle
        val subIdx = strMap["Department of Computer Science & Engineering - Semester IV Consolidated Grade Sheet"] ?: 1
        sb.append("""<row r="2" ht="24" customHeight="1">""")
        sb.append("""<c r="A2" s="10" t="s"><v>$subIdx</v></c>""")
        sb.append("""</row>""")

        // Row 3: Empty spacer
        sb.append("""<row r="3" ht="12" customHeight="1"/>""")

        // Row 4: Column Headers
        val headers = listOf("Roll No", "Student Name", "Section", "DSA (CS401)", "OS (CS402)", "DBMS (CS403)", "Maths (MA401)", "Total (400)", "Percentage", "SGPA", "Status")
        sb.append("""<row r="4" ht="26" customHeight="1">""")
        headers.forEachIndexed { idx, h ->
            val colLetter = ('A'.code + idx).toChar()
            val sIdx = strMap[h] ?: 0
            sb.append("""<c r="${colLetter}4" s="2" t="s"><v>$sIdx</v></c>""")
        }
        sb.append("""</row>""")

        // Student Data Rows
        val studentData = listOf(
            listOf("2103401", "Aarav Sharma", "CSE-A", "92", "88", "94", "96", "370", "92.5%", "9.62", "PASS - DISTINCTION"),
            listOf("2103402", "Ananya Verma", "CSE-A", "95", "91", "98", "92", "376", "94.0%", "9.80", "PASS - DISTINCTION"),
            listOf("2103403", "Rohan Patel", "CSE-A", "78", "82", "75", "84", "319", "79.8%", "8.25", "PASS - FIRST CLASS"),
            listOf("2103404", "Priya Singh", "CSE-B", "88", "85", "89", "90", "352", "88.0%", "9.10", "PASS - DISTINCTION"),
            listOf("2103405", "Ishaan Gupta", "CSE-B", "68", "72", "70", "64", "274", "68.5%", "7.15", "PASS - FIRST CLASS"),
            listOf("2103406", "Simran Kaur", "CSE-B", "94", "90", "92", "88", "364", "91.0%", "9.45", "PASS - DISTINCTION"),
            listOf("2103407", "Vikram Malhotra", "CSE-C", "82", "78", "85", "80", "325", "81.3%", "8.40", "PASS - FIRST CLASS"),
            listOf("2103408", "Meera Joshi", "CSE-C", "74", "76", "79", "72", "301", "75.3%", "7.80", "PASS - FIRST CLASS"),
            listOf("2103409", "Aditya Rao", "CSE-A", "89", "92", "91", "87", "359", "89.8%", "9.25", "PASS - DISTINCTION"),
            listOf("2103410", "Sneha Nair", "CSE-B", "91", "89", "94", "93", "367", "91.8%", "9.50", "PASS - DISTINCTION"),
            listOf("2103411", "Kavya Patel", "CSE-B", "85", "81", "88", "84", "338", "84.5%", "8.75", "PASS - DISTINCTION"),
            listOf("2103412", "Harpreet Singh", "CSE-C", "76", "72", "78", "70", "296", "74.0%", "7.60", "PASS - FIRST CLASS"),
            listOf("2103413", "Pooja Reddy", "CSE-A", "90", "86", "92", "89", "357", "89.3%", "9.20", "PASS - DISTINCTION"),
            listOf("2103414", "Nikhil Kumar", "CSE-C", "65", "68", "72", "60", "265", "66.3%", "6.90", "PASS - FIRST CLASS"),
            listOf("2103415", "Divya Menon", "CSE-B", "87", "84", "90", "86", "347", "86.8%", "8.95", "PASS - DISTINCTION"),
            listOf("2103416", "Arjun Kapoor", "CSE-A", "80", "79", "83", "81", "323", "80.8%", "8.35", "PASS - FIRST CLASS"),
            listOf("2103417", "Tanvi Bhatia", "CSE-C", "93", "95", "96", "94", "378", "94.5%", "9.85", "PASS - DISTINCTION"),
            listOf("2103418", "Manish Tiwari", "CSE-A", "71", "74", "70", "68", "283", "70.8%", "7.30", "PASS - FIRST CLASS"),
            listOf("2103419", "Rhea Sengupta", "CSE-B", "86", "88", "84", "82", "340", "85.0%", "8.80", "PASS - DISTINCTION"),
            listOf("2103420", "Kabir Bedi", "CSE-C", "79", "83", "80", "78", "320", "80.0%", "8.30", "PASS - FIRST CLASS")
        )

        studentData.forEachIndexed { rowIdx, rowData ->
            val rNum = rowIdx + 5
            val isZebra = (rowIdx % 2 == 1)
            val styleLeft = if (isZebra) 7 else 3
            val styleCenter = if (isZebra) 8 else 4
            val styleRight = if (isZebra) 9 else 5

            sb.append("""<row r="$rNum" ht="22" customHeight="1">""")
            // Roll No
            sb.append("""<c r="A$rNum" s="$styleCenter"><v>${rowData[0]}</v></c>""")
            // Name
            val nameIdx = strMap[rowData[1]] ?: 0
            sb.append("""<c r="B$rNum" s="$styleLeft" t="s"><v>$nameIdx</v></c>""")
            // Section
            val secIdx = strMap[rowData[2]] ?: 0
            sb.append("""<c r="C$rNum" s="$styleCenter" t="s"><v>$secIdx</v></c>""")
            // Marks
            sb.append("""<c r="D$rNum" s="$styleRight"><v>${rowData[3]}</v></c>""")
            sb.append("""<c r="E$rNum" s="$styleRight"><v>${rowData[4]}</v></c>""")
            sb.append("""<c r="F$rNum" s="$styleRight"><v>${rowData[5]}</v></c>""")
            sb.append("""<c r="G$rNum" s="$styleRight"><v>${rowData[6]}</v></c>""")
            // Total
            sb.append("""<c r="H$rNum" s="6"><v>${rowData[7]}</v></c>""")
            // Percentage
            val pIdx = strMap[rowData[8]] ?: 0
            sb.append("""<c r="I$rNum" s="$styleCenter" t="inlineStr"><is><t>${rowData[8]}</t></is></c>""")
            // SGPA
            sb.append("""<c r="J$rNum" s="6"><v>${rowData[9]}</v></c>""")
            // Status
            val status = rowData[10]
            val statusStyle = if (status.contains("DISTINCTION")) 12 else styleCenter
            val statusIdx = strMap[status] ?: 0
            sb.append("""<c r="K$rNum" s="$statusStyle" t="s"><v>$statusIdx</v></c>""")

            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")

        // Merged ranges for title banners
        sb.append("""<mergeCells count="2">""")
        sb.append("""<mergeCell ref="A1:K1"/>""")
        sb.append("""<mergeCell ref="A2:K2"/>""")
        sb.append("""</mergeCells>""")

        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildSheet2Xml(strMap: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        sb.append("""<cols>""")
        sb.append("""<col min="1" max="1" width="16" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="30" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="24" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="16" customWidth="1"/>""")
        sb.append("""<col min="5" max="5" width="16" customWidth="1"/>""")
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        // Title
        val tIdx = strMap["CGC CSE Department - Semester Performance Metrics & KPI Summary"] ?: 0
        sb.append("""<row r="1" ht="34" customHeight="1">""")
        sb.append("""<c r="A1" s="1" t="s"><v>$tIdx</v></c>""")
        sb.append("""</row>""")

        sb.append("""<row r="2" ht="12" customHeight="1"/>""")

        // Metrics Table Header
        val mIdx = strMap["Metric"] ?: 0
        val vIdx = strMap["Count / Value"] ?: 0
        sb.append("""<row r="3" ht="26" customHeight="1">""")
        sb.append("""<c r="A3" s="2" t="s"><v>$mIdx</v></c>""")
        sb.append("""<c r="B3" s="2" t="s"><v>$vIdx</v></c>""")
        sb.append("""</row>""")

        val kpiData = listOf(
            "Total Enrolled Students" to "120",
            "Total Appeared" to "118",
            "Total Passed" to "112",
            "Distinction (>75%)" to "48",
            "First Division (60-74%)" to "64",
            "Department Pass Rate" to "94.8%",
            "Department Average SGPA" to "7.96",
            "Highest SGPA" to "9.85"
        )

        kpiData.forEachIndexed { i, (k, v) ->
            val rNum = i + 4
            val kIdx = strMap[k] ?: 0
            sb.append("""<row r="$rNum" ht="24" customHeight="1">""")
            sb.append("""<c r="A$rNum" s="10" t="s"><v>$kIdx</v></c>""")
            if (v.endsWith("%") || v.contains(".")) {
                sb.append("""<c r="B$rNum" s="11" t="inlineStr"><is><t>$v</t></is></c>""")
            } else {
                sb.append("""<c r="B$rNum" s="11"><v>$v</v></c>""")
            }
            sb.append("""</row>""")
        }

        sb.append("""<row r="13" ht="16" customHeight="1"/>""")

        // Course breakdown table
        val courseHeaders = listOf("Subject Code", "Subject Name", "Faculty In-Charge", "Avg Attendance", "Course Pass %")
        sb.append("""<row r="14" ht="26" customHeight="1">""")
        courseHeaders.forEachIndexed { c, h ->
            val colLetter = ('A'.code + c).toChar()
            val hIdx = strMap[h] ?: 0
            sb.append("""<c r="${colLetter}14" s="2" t="s"><v>$hIdx</v></c>""")
        }
        sb.append("""</row>""")

        val courses = listOf(
            listOf("CS401", "Data Structures & Algorithms", "Dr. Harpreet Singh", "89.4%", "95.2%"),
            listOf("CS402", "Operating Systems", "Prof. Neha Sharma", "86.1%", "91.8%"),
            listOf("CS403", "Database Management Systems", "Dr. Rajesh Kumar", "91.5%", "97.5%"),
            listOf("MA401", "Discrete Mathematics", "Prof. Amit Verma", "84.2%", "88.6%")
        )

        courses.forEachIndexed { i, row ->
            val rNum = i + 15
            sb.append("""<row r="$rNum" ht="24" customHeight="1">""")
            val c0 = strMap[row[0]] ?: 0
            val c1 = strMap[row[1]] ?: 0
            val c2 = strMap[row[2]] ?: 0
            sb.append("""<c r="A$rNum" s="6" t="s"><v>$c0</v></c>""")
            sb.append("""<c r="B$rNum" s="3" t="s"><v>$c1</v></c>""")
            sb.append("""<c r="C$rNum" s="3" t="s"><v>$c2</v></c>""")
            sb.append("""<c r="D$rNum" s="4" t="inlineStr"><is><t>${row[3]}</t></is></c>""")
            sb.append("""<c r="E$rNum" s="12" t="inlineStr"><is><t>${row[4]}</t></is></c>""")
            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")

        sb.append("""<mergeCells count="1">""")
        sb.append("""<mergeCell ref="A1:E1"/>""")
        sb.append("""</mergeCells>""")

        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildSheet3Xml(strMap: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        sb.append("""<cols>""")
        sb.append("""<col min="1" max="1" width="14" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="22" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="16" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="16" customWidth="1"/>""")
        sb.append("""<col min="5" max="5" width="16" customWidth="1"/>""")
        sb.append("""<col min="6" max="6" width="16" customWidth="1"/>""")
        sb.append("""<col min="7" max="7" width="18" customWidth="1"/>""")
        sb.append("""<col min="8" max="8" width="16" customWidth="1"/>""")
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        // Title
        val tIdx = strMap["CGC Academic Year 2025-2026 Tuition Fee & Dues Ledger"] ?: 0
        sb.append("""<row r="1" ht="34" customHeight="1">""")
        sb.append("""<c r="A1" s="1" t="s"><v>$tIdx</v></c>""")
        sb.append("""</row>""")

        sb.append("""<row r="2" ht="12" customHeight="1"/>""")

        // Headers
        val feeHeaders = listOf("Roll No", "Student Name", "Annual Fee", "Paid Amount", "Balance Due", "Scholarship", "Payment Status", "Receipt No")
        sb.append("""<row r="3" ht="26" customHeight="1">""")
        feeHeaders.forEachIndexed { idx, h ->
            val colLetter = ('A'.code + idx).toChar()
            val hIdx = strMap[h] ?: 0
            sb.append("""<c r="${colLetter}3" s="2" t="s"><v>$hIdx</v></c>""")
        }
        sb.append("""</row>""")

        val feeData = listOf(
            listOf("2103401", "Aarav Sharma", "₹ 95,000", "₹ 95,000", "₹ 0", "MERIT 20%", "PAID", "REC-88401"),
            listOf("2103402", "Ananya Verma", "₹ 95,000", "₹ 95,000", "₹ 0", "MERIT 20%", "PAID", "REC-88402"),
            listOf("2103403", "Rohan Patel", "₹ 95,000", "₹ 50,000", "₹ 45,000", "NONE", "PARTIAL", "REC-88403"),
            listOf("2103404", "Priya Singh", "₹ 95,000", "₹ 95,000", "₹ 0", "SPORTS 15%", "PAID", "REC-88404"),
            listOf("2103405", "Ishaan Gupta", "₹ 95,000", "₹ 0", "₹ 95,000", "NONE", "PENDING", "-"),
            listOf("2103406", "Simran Kaur", "₹ 95,000", "₹ 95,000", "₹ 0", "MERIT 20%", "PAID", "REC-88405"),
            listOf("2103407", "Vikram Malhotra", "₹ 95,000", "₹ 60,000", "₹ 35,000", "NONE", "PARTIAL", "REC-88406"),
            listOf("2103408", "Meera Joshi", "₹ 95,000", "₹ 95,000", "₹ 0", "NONE", "PAID", "REC-88407"),
            listOf("2103409", "Aditya Rao", "₹ 95,000", "₹ 95,000", "₹ 0", "MERIT 20%", "PAID", "REC-88408"),
            listOf("2103410", "Sneha Nair", "₹ 95,000", "₹ 95,000", "₹ 0", "SPORTS 15%", "PAID", "REC-88409")
        )

        feeData.forEachIndexed { i, row ->
            val rNum = i + 4
            val isZebra = (i % 2 == 1)
            val stCenter = if (isZebra) 8 else 4
            val stLeft = if (isZebra) 7 else 3
            val stRight = if (isZebra) 9 else 5

            sb.append("""<row r="$rNum" ht="22" customHeight="1">""")
            sb.append("""<c r="A$rNum" s="$stCenter"><v>${row[0]}</v></c>""")
            val nameIdx = strMap[row[1]] ?: 0
            sb.append("""<c r="B$rNum" s="$stLeft" t="s"><v>$nameIdx</v></c>""")
            sb.append("""<c r="C$rNum" s="$stRight" t="inlineStr"><is><t>${row[2]}</t></is></c>""")
            sb.append("""<c r="D$rNum" s="$stRight" t="inlineStr"><is><t>${row[3]}</t></is></c>""")
            sb.append("""<c r="E$rNum" s="$stRight" t="inlineStr"><is><t>${row[4]}</t></is></c>""")
            val scholIdx = strMap[row[5]] ?: 0
            sb.append("""<c r="F$rNum" s="$stCenter" t="s"><v>$scholIdx</v></c>""")

            val status = row[6]
            val stStyle = when (status) {
                "PAID" -> 12
                "PENDING" -> 13
                else -> 8
            }
            val statusIdx = strMap[status] ?: 0
            sb.append("""<c r="G$rNum" s="$stStyle" t="s"><v>$statusIdx</v></c>""")
            sb.append("""<c r="H$rNum" s="$stCenter" t="inlineStr"><is><t>${row[7]}</t></is></c>""")
            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")

        sb.append("""<mergeCells count="1">""")
        sb.append("""<mergeCell ref="A1:H1"/>""")
        sb.append("""</mergeCells>""")

        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildSheet4Xml(strMap: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        sb.append("""<cols>""")
        sb.append("""<col min="1" max="1" width="12" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="18" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="14" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="22" customWidth="1"/>""")
        sb.append("""<col min="5" max="5" width="20" customWidth="1"/>""")
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        val tIdx = strMap["CGC Academic Grading System & Credit Regulations"] ?: 0
        sb.append("""<row r="1" ht="34" customHeight="1">""")
        sb.append("""<c r="A1" s="1" t="s"><v>$tIdx</v></c>""")
        sb.append("""</row>""")

        sb.append("""<row r="2" ht="12" customHeight="1"/>""")

        val gradeHeaders = listOf("Grade", "Marks Range", "Grade Point", "Performance Level", "Classification")
        sb.append("""<row r="3" ht="26" customHeight="1">""")
        gradeHeaders.forEachIndexed { idx, h ->
            val colLetter = ('A'.code + idx).toChar()
            val hIdx = strMap[h] ?: 0
            sb.append("""<c r="${colLetter}3" s="2" t="s"><v>$hIdx</v></c>""")
        }
        sb.append("""</row>""")

        val gradeData = listOf(
            listOf("O", "90 - 100", "10.0", "Outstanding", "Distinction"),
            listOf("A+", "80 - 89", "9.0", "Excellent", "Distinction"),
            listOf("A", "70 - 79", "8.0", "Very Good", "First Class"),
            listOf("B+", "60 - 69", "7.0", "Good", "First Class"),
            listOf("B", "50 - 59", "6.0", "Above Average", "Second Class"),
            listOf("C", "40 - 49", "5.0", "Average", "Pass Class"),
            listOf("P", "35 - 39", "4.0", "Pass", "Pass"),
            listOf("F", "Below 35", "0.0", "Fail", "Re-appear")
        )

        gradeData.forEachIndexed { i, row ->
            val rNum = i + 4
            val isZebra = (i % 2 == 1)
            val stCenter = if (isZebra) 8 else 4
            val stLeft = if (isZebra) 7 else 3

            sb.append("""<row r="$rNum" ht="22" customHeight="1">""")
            val gIdx = strMap[row[0]] ?: 0
            sb.append("""<c r="A$rNum" s="6" t="s"><v>$gIdx</v></c>""")
            val mIdx = strMap[row[1]] ?: 0
            sb.append("""<c r="B$rNum" s="$stCenter" t="s"><v>$mIdx</v></c>""")
            sb.append("""<c r="C$rNum" s="$stCenter"><v>${row[2]}</v></c>""")
            val pIdx = strMap[row[3]] ?: 0
            sb.append("""<c r="D$rNum" s="$stLeft" t="s"><v>$pIdx</v></c>""")
            val cIdx = strMap[row[4]] ?: 0
            sb.append("""<c r="E$rNum" s="$stCenter" t="s"><v>$cIdx</v></c>""")
            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")

        sb.append("""<mergeCells count="1">""")
        sb.append("""<mergeCell ref="A1:E1"/>""")
        sb.append("""</mergeCells>""")

        sb.append("""</worksheet>""")
        return sb.toString()
    }
}
