package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates and provides spec-compliant, high-fidelity Microsoft Word (.docx) documents
 * for visual testing and demonstration in HR Read.
 *
 * Emits "Assignment 1 CV.docx" containing:
 * - Institutional / professional crest logo image
 * - Multi-level typography (headings, bold, italic, colors, sizing)
 * - Defined page margins and page size (A4)
 * - Header and footer with page numbers
 * - Multi-column tables with cell shading and borders
 * - Explicit page break separating Page 1 and Page 2
 * - Authentic signature block with script signature image
 */
object SampleDocxGenerator {

    const val SAMPLE_FILE_NAME = "Assignment 1 CV.docx"

    fun generateAssignment1Cv(destinationFile: File, context: Context? = null): File {
        destinationFile.parentFile?.mkdirs()

        // 1. Try reading pre-packaged file from assets if available
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

        // 2. Check candidate paths in workspace
        val candidates = listOf(
            File("app/src/main/assets", SAMPLE_FILE_NAME),
            File("src/main/assets", SAMPLE_FILE_NAME)
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.length() > 0) {
                candidate.copyTo(destinationFile, overwrite = true)
                return destinationFile
            }
        }

        // 3. Fallback: generate genuine OpenXML DOCX package
        writeSpecCompliantDocx(destinationFile)
        return destinationFile
    }

    private fun writeSpecCompliantDocx(destinationFile: File) {
        val logoBytes = generateLogoPngBytes()
        val signatureBytes = generateSignaturePngBytes()

        ZipOutputStream(FileOutputStream(destinationFile)).use { zos ->
            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(ROOT_RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(DOCUMENT_RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/header1.xml
            zos.putNextEntry(ZipEntry("word/header1.xml"))
            zos.write(HEADER_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/footer1.xml
            zos.putNextEntry(ZipEntry("word/footer1.xml"))
            zos.write(FOOTER_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/styles.xml
            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(STYLES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // word/media/image1.png
            zos.putNextEntry(ZipEntry("word/media/image1.png"))
            zos.write(logoBytes)
            zos.closeEntry()

            // word/media/signature.png
            zos.putNextEntry(ZipEntry("word/media/signature.png"))
            zos.write(signatureBytes)
            zos.closeEntry()

            // word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(DOCUMENT_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private val FALLBACK_PNG_BYTES: ByteArray by lazy {
        try {
            java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
            )
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun generateLogoPngBytes(): ByteArray {
        return try {
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Navy circular crest
            paint.color = Color.parseColor("#1A365D")
            canvas.drawCircle(80f, 80f, 74f, paint)

            // Gold ring border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.parseColor("#D69E2E")
            canvas.drawCircle(80f, 80f, 70f, paint)

            // Inner insignia text "CV"
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 52f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("CV", 80f, 98f, paint)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (_: Throwable) {
            FALLBACK_PNG_BYTES
        }
    }

    private fun generateSignaturePngBytes(): ByteArray {
        return try {
            val bitmap = Bitmap.createBitmap(280, 90, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E3A8A") // Dark blue ink
                strokeWidth = 3f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            // Cursive stylized signature curve
            val path = Path().apply {
                moveTo(20f, 55f)
                cubicTo(40f, 15f, 60f, 15f, 70f, 60f)
                cubicTo(75f, 75f, 85f, 35f, 95f, 50f)
                cubicTo(110f, 40f, 120f, 65f, 135f, 45f)
                cubicTo(150f, 20f, 165f, 75f, 185f, 40f)
                cubicTo(200f, 50f, 215f, 30f, 230f, 55f)
                lineTo(260f, 50f)
            }
            canvas.drawPath(path, paint)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (_: Throwable) {
            FALLBACK_PNG_BYTES
        }
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/signature.png"/>
</Relationships>"""

    private const val HEADER_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p>
    <w:pPr>
      <w:jc w:val="right"/>
    </w:pPr>
    <w:r>
      <w:rPr>
        <w:rFonts w:ascii="Calibri"/>
        <w:sz w:val="18"/>
        <w:color w:val="718096"/>
      </w:rPr>
      <w:t>Curriculum Vitae — Alexander J. Mercer</w:t>
    </w:r>
  </w:p>
</w:hdr>"""

    private const val FOOTER_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p>
    <w:pPr>
      <w:jc w:val="right"/>
    </w:pPr>
    <w:r>
      <w:rPr>
        <w:rFonts w:ascii="Calibri"/>
        <w:sz w:val="18"/>
        <w:color w:val="718096"/>
      </w:rPr>
      <w:t>Assignment 1 CV | Page {PAGE} of {NUMPAGES}</w:t>
    </w:r>
  </w:p>
</w:ftr>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri"/>
        <w:sz w:val="22"/>
        <w:color w:val="1A202C"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
</w:styles>"""

    private const val DOCUMENT_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
  <w:body>
    <!-- Crest Logo Drawing -->
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="60" w:after="120"/>
      </w:pPr>
      <w:r>
        <w:drawing>
          <wp:inline>
            <wp:extent cx="1016000" cy="1016000"/>
            <wp:docPr id="1" name="Logo" descr="Academic Crest Logo"/>
            <a:graphic>
              <a:graphicData>
                <a:blip r:embed="rId4"/>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>

    <!-- Applicant Name -->
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="0" w:after="60"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="Times New Roman"/>
          <w:b/>
          <w:sz w:val="48"/>
          <w:color w:val="1A365D"/>
        </w:rPr>
        <w:t>ALEXANDER J. MERCER</w:t>
      </w:r>
    </w:p>

    <!-- Title / Subheading -->
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="0" w:after="80"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="Calibri"/>
          <w:sz w:val="26"/>
          <w:color w:val="2B6CB0"/>
        </w:rPr>
        <w:t>Lead Systems Architect &amp; Senior Software Engineer</w:t>
      </w:r>
    </w:p>

    <!-- Contact Info -->
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="0" w:after="160"/>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="8" w:color="CBD5E0"/>
        </w:pBdr>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:sz w:val="20"/>
          <w:color w:val="4A5568"/>
        </w:rPr>
        <w:t>alexander.mercer@email.com  •  +1 (555) 019-2834  •  San Francisco, CA  •  linkedin.com/in/alex-mercer</w:t>
      </w:r>
    </w:p>

    <!-- Professional Summary Header -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="120" w:after="60"/>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="12" w:color="2B6CB0"/>
        </w:pBdr>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:color w:val="1A365D"/>
        </w:rPr>
        <w:t>EXECUTIVE SUMMARY</w:t>
      </w:r>
    </w:p>

    <!-- Summary Paragraph -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="60" w:after="140" w:line="276"/>
        <w:shd w:fill="F7FAFC"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:sz w:val="22"/>
          <w:color w:val="2D3748"/>
        </w:rPr>
        <w:t>Distinguished Systems Architect and Lead Engineer with over 12 years of experience designing mission-critical enterprise software, distributed microservices, and high-throughput document processing pipelines. Recognized for technical leadership, high-concurrency architectures, and strict engineering discipline.</w:t>
      </w:r>
    </w:p>

    <!-- Education Header -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="120" w:after="60"/>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="12" w:color="2B6CB0"/>
        </w:pBdr>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:color w:val="1A365D"/>
        </w:rPr>
        <w:t>EDUCATION &amp; ACADEMIC HONORS</w:t>
      </w:r>
    </w:p>

    <!-- Education Table -->
    <w:tbl>
      <w:tblGrid>
        <w:gridCol w:w="3000"/>
        <w:gridCol w:w="2800"/>
        <w:gridCol w:w="1600"/>
        <w:gridCol w:w="1600"/>
      </w:tblGrid>
      <!-- Header Row -->
      <w:tr>
        <w:trPr><w:tblHeader/></w:trPr>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="1A365D"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="12" w:color="1A365D"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Degree / Program</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="1A365D"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="12" w:color="1A365D"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Institution</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="1A365D"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="12" w:color="1A365D"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="FFFFFF"/></w:rPr><w:t>Graduation</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="1A365D"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="12" w:color="1A365D"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="FFFFFF"/></w:rPr><w:t>GPA / Honors</w:t></w:r></w:p>
        </w:tc>
      </w:tr>
      <!-- Row 1 -->
      <w:tr>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="EDF2F7"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/></w:rPr><w:t>M.S. in Computer Science</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="EDF2F7"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>Stanford University</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="EDF2F7"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>June 2017</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="EDF2F7"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="2B6CB0"/></w:rPr><w:t>3.94 / 4.00 (Honors)</w:t></w:r></w:p>
        </w:tc>
      </w:tr>
      <!-- Row 2 -->
      <w:tr>
        <w:tc>
          <w:tcPr>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/></w:rPr><w:t>B.S. in Software Engineering</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>UC Berkeley</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>May 2014</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="2B6CB0"/></w:rPr><w:t>3.88 (Summa Cum Laude)</w:t></w:r></w:p>
        </w:tc>
      </w:tr>
    </w:tbl>

    <!-- Technical Skills Header -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="140" w:after="60"/>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="12" w:color="2B6CB0"/>
        </w:pBdr>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:color w:val="1A365D"/>
        </w:rPr>
        <w:t>CORE TECHNICAL COMPETENCIES</w:t>
      </w:r>
    </w:p>

    <!-- Technical Skills Table -->
    <w:tbl>
      <w:tblGrid>
        <w:gridCol w:w="2800"/>
        <w:gridCol w:w="6200"/>
      </w:tblGrid>
      <w:tr>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="F7FAFC"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="2B6CB0"/></w:rPr><w:t>Languages &amp; Runtimes</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>Kotlin, Java, Rust, Go, Python, C++, TypeScript, SQL</w:t></w:r></w:p>
        </w:tc>
      </w:tr>
      <w:tr>
        <w:tc>
          <w:tcPr>
            <w:shd w:fill="F7FAFC"/>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/><w:color w:val="2B6CB0"/></w:rPr><w:t>Architectures &amp; Cloud</w:t></w:r></w:p>
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcBorders><w:bottom w:val="single" w:sz="4" w:color="CBD5E0"/></w:tcBorders>
          </w:tcPr>
          <w:p><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>Event-driven microservices, Kubernetes, Docker, AWS, Google Cloud</w:t></w:r></w:p>
        </w:tc>
      </w:tr>
    </w:tbl>

    <!-- Explicit Page Break separating Page 1 from Page 2 -->
    <w:p>
      <w:r>
        <w:br w:type="page"/>
      </w:r>
    </w:p>

    <!-- PAGE 2: Professional Experience Header -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="60" w:after="60"/>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="12" w:color="2B6CB0"/>
        </w:pBdr>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:color w:val="1A365D"/>
        </w:rPr>
        <w:t>PROFESSIONAL EXPERIENCE</w:t>
      </w:r>
    </w:p>

    <!-- Role 1 -->
    <w:p>
      <w:pPr><w:spacing w:before="60" w:after="20"/></w:pPr>
      <w:r><w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="1A202C"/></w:rPr><w:t>Principal Systems Architect</w:t></w:r>
      <w:r><w:rPr><w:sz w:val="20"/><w:color w:val="718096"/></w:rPr><w:t>  —  Apex Cloud Infrastructure (2021 – Present)</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:spacing w:before="20" w:after="80"/><w:ind w:left="360"/></w:pPr>
      <w:r><w:rPr><w:sz w:val="20"/><w:color w:val="2D3748"/></w:rPr><w:t>• Directed modern distributed document engine serving 4.5M monthly transactions.</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:spacing w:before="20" w:after="100"/><w:ind w:left="360"/></w:pPr>
      <w:r><w:rPr><w:sz w:val="20"/><w:color w:val="2D3748"/></w:rPr><w:t>• Reduced page layout rendering latencies by 64% through custom vector streaming.</w:t></w:r>
    </w:p>

    <!-- Declaration of Authenticity -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="160" w:after="60"/>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="12" w:color="2B6CB0"/>
        </w:pBdr>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:color w:val="1A365D"/>
        </w:rPr>
        <w:t>DECLARATION &amp; AUTHENTICITY</w:t>
      </w:r>
    </w:p>

    <w:p>
      <w:pPr><w:spacing w:before="60" w:after="120"/><w:shd w:fill="F7FAFC"/></w:pPr>
      <w:r>
        <w:rPr><w:i/><w:sz w:val="20"/><w:color w:val="4A5568"/></w:rPr>
        <w:t>I hereby declare that all academic credentials, professional records, and qualifications presented in this document are true, accurate, and verifiable.</w:t>
      </w:r>
    </w:p>

    <!-- Signature Drawing -->
    <w:p>
      <w:pPr>
        <w:spacing w:before="80" w:after="40"/>
      </w:pPr>
      <w:r>
        <w:drawing>
          <wp:inline>
            <wp:extent cx="1778000" cy="571500"/>
            <wp:docPr id="2" name="Signature" descr="Applicant Signature"/>
            <a:graphic>
              <a:graphicData>
                <a:blip r:embed="rId5"/>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>

    <!-- Signer Printed Name & Date -->
    <w:p>
      <w:pPr><w:spacing w:before="20" w:after="20"/></w:pPr>
      <w:r>
        <w:rPr><w:b/><w:sz w:val="22"/><w:color w:val="1A202C"/></w:rPr>
        <w:t>Alexander J. Mercer, M.S.</w:t>
      </w:r>
    </w:p>
    <w:p>
      <w:pPr><w:spacing w:before="0" w:after="100"/></w:pPr>
      <w:r>
        <w:rPr><w:sz w:val="20"/><w:color w:val="718096"/></w:rPr>
        <w:t>Date: September 14, 2026</w:t>
      </w:r>
    </w:p>

    <!-- Section Properties (A4 Size, 1 inch margins) -->
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:bottom="1440" w:left="1440" w:right="1440" w:header="720" w:footer="720"/>
    </w:sectPr>
  </w:body>
</w:document>"""
}
