package com.example.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Generates and provides high-fidelity, real multi-page vector PDF documents for testing and sample preview.
 *
 * Includes varied page dimensions (standard portrait and wide landscape), multi-column tables,
 * vector borders, colored header panels, and realistic student roster data.
 */
object SamplePdfGenerator {

    const val SAMPLE_FILE_NAME = "Students List.pdf"

    fun generateStudentsListPdf(destinationFile: File, context: Context? = null): File {
        destinationFile.parentFile?.mkdirs()

        // 1. Try reading pre-packaged Students List.pdf from assets if context is present
        if (context != null) {
            try {
                context.assets.open(SAMPLE_FILE_NAME).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destinationFile.exists() && destinationFile.length() > 0) {
                    return destinationFile
                }
            } catch (_: Exception) {}
        }

        // 2. Also check if assets file exists in relative workspace paths (during JVM/Robolectric test runs)
        val candidatePaths = listOf(
            File("app/src/main/assets", SAMPLE_FILE_NAME),
            File("src/main/assets", SAMPLE_FILE_NAME)
        )
        for (candidate in candidatePaths) {
            if (candidate.exists() && candidate.length() > 0) {
                candidate.copyTo(destinationFile, overwrite = true)
                return destinationFile
            }
        }

        // 3. Fallback: generate spec-compliant 3-page PDF with portrait and landscape pages
        writeSpecCompliantStudentsPdf(destinationFile)
        return destinationFile
    }

    private fun writeSpecCompliantStudentsPdf(destinationFile: File) {
        val pagesContent = listOf(
            // Page 1 (Portrait 595x842)
            Triple(595, 842, listOf(
                "0.118 0.227 0.541 rg 0 770 595 72 re f",
                "1 1 1 rg /F1 18 Tf 32 800 Td (Students List - Computer Science (Fall 2026)) Tj",
                "0.8 0.85 0.9 rg /F1 10 Tf 0 -18 Td (Page 1 of 3 - Department of Computer Science & Engineering) Tj",
                "0 0 0 rg /F1 12 Tf",
                "0.95 0.96 0.98 rg 32 680 531 24 re f",
                "0.28 0.33 0.41 rg /F1 10 Tf 42 688 Td (ID      FULL NAME             EMAIL                             MAJOR                  GPA    STATUS) Tj",
                "0.06 0.09 0.16 rg /F1 10 Tf",
                "0 -28 Td (CS-101 Emma Watson           emma.w@university.edu             Computer Science       3.92   Active) Tj",
                "0 -24 Td (CS-102 James Rodriguez       james.r@university.edu            Software Eng.          3.78   Active) Tj",
                "0 -24 Td (CS-103 Aaliyah Patel         aaliyah.p@university.edu          AI & Robotics          3.95   Active) Tj",
                "0 -24 Td (CS-104 Lucas Vance           lucas.v@university.edu            Cybersecurity          3.64   Probation) Tj",
                "0 -24 Td (CS-105 Sophia Chen           sophia.c@university.edu           Data Science           3.88   Active) Tj",
                "0 -24 Td (CS-106 Benjamin Clark        b.clark@university.edu            Computer Science       3.45   Active) Tj",
                "0 -24 Td (CS-107 Mia Tanaka            mia.t@university.edu              AI & Robotics          3.91   Active) Tj",
                "0 -24 Td (CS-108 Ethan Wright          ethan.w@university.edu            Software Eng.          3.82   Active) Tj"
            )),
            // Page 2 (Portrait 595x842)
            Triple(595, 842, listOf(
                "0.118 0.227 0.541 rg 0 770 595 72 re f",
                "1 1 1 rg /F1 18 Tf 32 800 Td (Students List - Electrical Engineering (Fall 2026)) Tj",
                "0.8 0.85 0.9 rg /F1 10 Tf 0 -18 Td (Page 2 of 3 - Department of Electrical & Computer Engineering) Tj",
                "0 0 0 rg /F1 12 Tf",
                "0.95 0.96 0.98 rg 32 680 531 24 re f",
                "0.28 0.33 0.41 rg /F1 10 Tf 42 688 Td (ID      FULL NAME             EMAIL                             MAJOR                  GPA    STATUS) Tj",
                "0.06 0.09 0.16 rg /F1 10 Tf",
                "0 -28 Td (EE-201 Alexander Davis       alex.d@university.edu             VLSI Systems           3.85   Active) Tj",
                "0 -24 Td (EE-202 Chloe Bennett         chloe.b@university.edu            Signal Processing      3.71   Active) Tj",
                "0 -24 Td (EE-203 Daniel Kim            daniel.k@university.edu           Embedded Systems       3.93   Active) Tj",
                "0 -24 Td (EE-204 Hannah Scott          hannah.s@university.edu           Power Systems          3.62   Active) Tj",
                "0 -24 Td (EE-205 Oliver Martinez       oliver.m@university.edu           Robotics               3.89   Active) Tj",
                "0 -24 Td (EE-206 Zoe Anderson          zoe.a@university.edu              Telecommunications     3.75   Active) Tj"
            )),
            // Page 3 (Landscape 842x595)
            Triple(842, 595, listOf(
                "0.059 0.09 0.165 rg 0 535 842 60 re f",
                "1 1 1 rg /F1 18 Tf 36 565 Td (Comprehensive Semester Analytics & Grading Matrix (Expanded Landscape)) Tj",
                "0.58 0.64 0.72 rg /F1 10 Tf 0 -18 Td (Page 3 of 3 - Wide-Format Blueprint / Multi-Column View (842 x 595 pt)) Tj",
                "0.93 0.95 0.98 rg 36 430 770 70 re f",
                "0 0 0 rg /F1 11 Tf 48 470 Td (Computer Science Department: 342 Students Enrolled | Average GPA: 3.68 | 98.2% Retention) Tj",
                "0.15 0.39 0.92 rg 48 450 746 4 re f",
                "0.95 0.96 0.98 rg 36 380 770 24 re f",
                "0.28 0.33 0.41 rg /F1 10 Tf 48 388 Td (SEMESTER     COURSE CODE    COURSE TITLE                                   INSTRUCTOR         CREDITS   ENROLLMENT) Tj",
                "0.06 0.09 0.16 rg /F1 10 Tf",
                "0 -28 Td (Fall 2026    CS-401         Advanced Operating Systems & Kernel Design     Dr. Linus Tan      4         88 / 90) Tj",
                "0 -24 Td (Fall 2026    CS-482         Distributed Systems & Cloud Architecture       Dr. Sarah Connor   4         90 / 90) Tj",
                "0 -24 Td (Fall 2026    EE-340         Digital Signal Processing & Wavelets           Dr. Claude Shannon 3         65 / 75) Tj",
                "0 -24 Td (Fall 2026    MATH-310       Matrix Algebra & Numerical Optimization        Dr. Alan Turing    3         110 / 120) Tj"
            ))
        )

        val numPages = pagesContent.size
        val pageObjIndices = mutableListOf<Int>()
        val contentObjIndices = mutableListOf<Int>()

        var currentIdx = 4
        for (i in 0 until numPages) {
            pageObjIndices.add(currentIdx)
            contentObjIndices.add(currentIdx + 1)
            currentIdx += 2
        }

        val totalObjects = currentIdx - 1

        val catalogStr = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        val kidsStr = pageObjIndices.joinToString(" ") { "$it 0 R" }
        val pagesStr = "2 0 obj\n<< /Type /Pages /Kids [$kidsStr] /Count $numPages >>\nendobj\n"
        val fontStr = "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"

        val objContents = Array(totalObjects + 1) { ByteArray(0) }
        objContents[1] = catalogStr.toByteArray(Charsets.UTF_8)
        objContents[2] = pagesStr.toByteArray(Charsets.UTF_8)
        objContents[3] = fontStr.toByteArray(Charsets.UTF_8)

        for (i in 0 until numPages) {
            val (w, h, commands) = pagesContent[i]
            val pageIdx = pageObjIndices[i]
            val contentIdx = contentObjIndices[i]

            val pageStr = "$pageIdx 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
                    "/Resources << /Font << /F1 3 0 R >> >> /Contents $contentIdx 0 R >>\nendobj\n"
            objContents[pageIdx] = pageStr.toByteArray(Charsets.UTF_8)

            val streamData = "BT\n" + commands.joinToString("\n") + "\nET\n"
            val streamBytes = streamData.toByteArray(Charsets.UTF_8)
            val contentStr = "$contentIdx 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n${streamData}endstream\nendobj\n"
            objContents[contentIdx] = contentStr.toByteArray(Charsets.UTF_8)
        }

        FileOutputStream(destinationFile).use { fos ->
            fos.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".toByteArray(Charsets.ISO_8859_1))
            var currentOffset = 15L // %PDF-1.4\n%\xe2\xe3\xcf\xd3\n is 15 bytes

            val objOffsets = LongArray(totalObjects + 1)
            for (objNum in 1..totalObjects) {
                objOffsets[objNum] = currentOffset
                val bytes = objContents[objNum]
                fos.write(bytes)
                currentOffset += bytes.size
            }

            val xrefOffset = currentOffset
            val xrefHeader = "xref\n0 ${totalObjects + 1}\n0000000000 65535 f \n"
            fos.write(xrefHeader.toByteArray(Charsets.UTF_8))

            for (objNum in 1..totalObjects) {
                val line = String.format("%010d 00000 n \n", objOffsets[objNum])
                fos.write(line.toByteArray(Charsets.UTF_8))
            }

            val trailer = "trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
            fos.write(trailer.toByteArray(Charsets.UTF_8))
        }
    }
}
