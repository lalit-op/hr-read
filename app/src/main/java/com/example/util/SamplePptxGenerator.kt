package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generator for authentic, high-fidelity Microsoft PowerPoint (.pptx) presentations.
 * Produces valid OpenXML PresentationML archives conforming to standard specifications.
 */
object SamplePptxGenerator {

    const val SAMPLE_FILE_NAME = "Unit 2.3 (2).pptx"

    fun generateUnit23Presentation(targetFile: File, context: Context? = null): File {
        targetFile.parentFile?.mkdirs()

        val crestBytes = createUniversityCrestPng()
        val archDiagramBytes = createArchitectureDiagramPng()

        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

                // 2. _rels/.rels
                writeZipEntry(zos, "_rels/.rels", buildRootRelsXml())

                // 3. ppt/presentation.xml
                writeZipEntry(zos, "ppt/presentation.xml", buildPresentationXml())

                // 4. ppt/_rels/presentation.xml.rels
                writeZipEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRelsXml())

                // 5. ppt/theme/theme1.xml
                writeZipEntry(zos, "ppt/theme/theme1.xml", buildThemeXml())

                // 6. Media assets
                writeBinaryZipEntry(zos, "ppt/media/crest.png", crestBytes)
                writeBinaryZipEntry(zos, "ppt/media/architecture.png", archDiagramBytes)

                // 7. Slides and slide relationships
                // Slide 1: Title Slide
                writeZipEntry(zos, "ppt/slides/slide1.xml", buildSlide1Xml())
                writeZipEntry(zos, "ppt/slides/_rels/slide1.xml.rels", buildSlide1RelsXml())

                // Slide 2: Learning Objectives & Agenda
                writeZipEntry(zos, "ppt/slides/slide2.xml", buildSlide2Xml())
                writeZipEntry(zos, "ppt/slides/_rels/slide2.xml.rels", buildSimpleSlideRelsXml())

                // Slide 3: 5-Stage RISC Pipeline Architecture Diagram
                writeZipEntry(zos, "ppt/slides/slide3.xml", buildSlide3Xml())
                writeZipEntry(zos, "ppt/slides/_rels/slide3.xml.rels", buildSimpleSlideRelsXml())

                // Slide 4: Memory Hierarchy Performance Comparison Table
                writeZipEntry(zos, "ppt/slides/slide4.xml", buildSlide4Xml())
                writeZipEntry(zos, "ppt/slides/_rels/slide4.xml.rels", buildSimpleSlideRelsXml())

                // Slide 5: Embedded Media & Bus Co-Processor Overview
                writeZipEntry(zos, "ppt/slides/slide5.xml", buildSlide5Xml())
                writeZipEntry(zos, "ppt/slides/_rels/slide5.xml.rels", buildSlide5RelsXml())

                // Slide 6: Summary & Review Questions
                writeZipEntry(zos, "ppt/slides/slide6.xml", buildSlide6Xml())
                writeZipEntry(zos, "ppt/slides/_rels/slide6.xml.rels", buildSimpleSlideRelsXml())
            }
        }

        return targetFile
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun writeBinaryZipEntry(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun createUniversityCrestPng(): ByteArray {
        val width = 240
        val height = 240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw shield
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2563EB.toInt()
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(120f, 20f)
            lineTo(210f, 50f)
            lineTo(210f, 130f)
            quadTo(210f, 200f, 120f, 225f)
            quadTo(30f, 200f, 30f, 130f)
            lineTo(30f, 50f)
            close()
        }
        canvas.drawPath(path, shieldPaint)

        // Shield border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF59E0B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawPath(path, borderPaint)

        // Inner book emblem
        val bookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(70f, 80f, 170f, 140f), 8f, 8f, bookPaint)

        // Star on top
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF59E0B.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(120f, 60f, 12f, starPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun createArchitectureDiagramPng(): ByteArray {
        val width = 640
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(0xFF0F172A.toInt())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // CPU Core box
        paint.color = 0xFF1E293B.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(40f, 40f, 240f, 160f), 12f, 12f, paint)
        paint.color = 0xFF3B82F6.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(40f, 40f, 240f, 160f), 12f, 12f, paint)

        // CPU text
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("CPU Complex", 60f, 85f, paint)
        paint.textSize = 15f
        paint.isFakeBoldText = false
        paint.color = 0xFF94A3B8.toInt()
        canvas.drawText("8x Out-of-Order Cores", 60f, 120f, paint)
        canvas.drawText("32MB Shared L3 Cache", 60f, 142f, paint)

        // GPU / NPU box
        paint.color = 0xFF1E293B.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(40f, 190f, 240f, 310f), 12f, 12f, paint)
        paint.color = 0xFF10B981.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(40f, 190f, 240f, 310f), 12f, 12f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("GPU & NPU Cluster", 60f, 235f, paint)
        paint.textSize = 15f
        paint.isFakeBoldText = false
        paint.color = 0xFF94A3B8.toInt()
        canvas.drawText("Vector Matrix Unit", 60f, 270f, paint)
        canvas.drawText("45 TOPS Tensor Engine", 60f, 292f, paint)

        // Central System Bus
        paint.color = 0xFFF59E0B.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(280f, 30f, 340f, 320f), 10f, 10f, paint)
        paint.color = 0xFF0F172A.toInt()
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-90f, 310f, 180f)
        canvas.drawText("HIGH-SPEED INTERCONNECT BUS", 200f, 185f, paint)
        canvas.restore()

        // Memory Controller box
        paint.color = 0xFF1E293B.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(380f, 80f, 600f, 270f), 12f, 12f, paint)
        paint.color = 0xFF8B5CF6.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(380f, 80f, 600f, 270f), 12f, 12f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("Unified Memory", 410f, 130f, paint)
        paint.textSize = 15f
        paint.isFakeBoldText = false
        paint.color = 0xFF94A3B8.toInt()
        canvas.drawText("Quad-Channel LPDDR5X", 410f, 170f, paint)
        canvas.drawText("Bandwidth: 136 GB/s", 410f, 198f, paint)
        canvas.drawText("Hardware Cache Snooping", 410f, 226f, paint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Default Extension="png" ContentType="image/png"/>
            <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
            <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
            <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
            <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
            <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
            <Override PartName="/ppt/slides/slide4.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
            <Override PartName="/ppt/slides/slide5.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
            <Override PartName="/ppt/slides/slide6.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
        </Types>
    """.trimIndent()

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildPresentationXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <!-- 16:9 Widescreen slide dimensions: 12192000 x 6858000 EMUs = 960 x 540 pt -->
            <p:sldSz cx="12192000" cy="6858000" type="screen16x9"/>
            <p:sldIdLst>
                <p:sldId id="256" r:id="rId2"/>
                <p:sldId id="257" r:id="rId3"/>
                <p:sldId id="258" r:id="rId4"/>
                <p:sldId id="259" r:id="rId5"/>
                <p:sldId id="260" r:id="rId6"/>
                <p:sldId id="261" r:id="rId7"/>
            </p:sldIdLst>
        </p:presentation>
    """.trimIndent()

    private fun buildPresentationRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rIdTheme" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
            <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
            <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide4.xml"/>
            <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide5.xml"/>
            <Relationship Id="rId7" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide6.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildThemeXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Academic Modern">
            <a:themeElements>
                <a:clrScheme name="Modern Scholar">
                    <a:dk1><a:srgbClr val="0F172A"/></a:dk1>
                    <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
                    <a:dk2><a:srgbClr val="1E293B"/></a:dk2>
                    <a:lt2><a:srgbClr val="F8FAFC"/></a:lt2>
                    <a:accent1><a:srgbClr val="2563EB"/></a:accent1>
                    <a:accent2><a:srgbClr val="0D9488"/></a:accent2>
                    <a:accent3><a:srgbClr val="F59E0B"/></a:accent3>
                    <a:accent4><a:srgbClr val="E11D48"/></a:accent4>
                    <a:accent5><a:srgbClr val="7C3AED"/></a:accent5>
                    <a:accent6><a:srgbClr val="059669"/></a:accent6>
                    <a:hlink><a:srgbClr val="2563EB"/></a:hlink>
                    <a:folHlink><a:srgbClr val="7C3AED"/></a:folHlink>
                </a:clrScheme>
            </a:themeElements>
        </a:theme>
    """.trimIndent()

    private fun buildSlide1RelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rIdImg1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/crest.png"/>
        </Relationships>
    """.trimIndent()

    private fun buildSlide5RelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rIdImg2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/architecture.png"/>
        </Relationships>
    """.trimIndent()

    private fun buildSimpleSlideRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
    """.trimIndent()

    // Slide 1: High-impact Title Slide
    private fun buildSlide1Xml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:bg>
                    <p:bgPr>
                        <a:gradFill>
                            <a:gsLst>
                                <a:gs pos="0"><a:srgbClr val="0F172A"/></a:gs>
                                <a:gs pos="100000"><a:srgbClr val="1E293B"/></a:gs>
                            </a:gsLst>
                        </a:gradFill>
                    </p:bgPr>
                </p:bg>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>

                    <!-- Decorative top bar -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="AccentLine"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="762000"/><a:ext cx="10668000" cy="50800"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="2563EB"/></a:solidFill>
                        </p:spPr>
                    </p:sp>

                    <!-- University Crest Picture -->
                    <p:pic>
                        <p:nvPicPr><p:cNvPr id="3" name="CrestLogo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
                        <p:blipFill>
                            <a:blip r:embed="rIdImg1"/>
                            <a:stretch><a:fillRect/></a:stretch>
                        </p:blipFill>
                        <p:spPr>
                            <a:xfrm><a:off x="10000000" y="1000000"/><a:ext cx="1200000" cy="1200000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                        </p:spPr>
                    </p:pic>

                    <!-- Module Tag Badge -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="4" name="Badge"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="1100000"/><a:ext cx="3000000" cy="400000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="1E3A8A"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="3B82F6"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r>
                                    <a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="93C5FD"/></a:solidFill></a:rPr>
                                    <a:t>CS-304: SYSTEMS ARCHITECTURE</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Main Presentation Title -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="5" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="1700000"/><a:ext cx="10668000" cy="1800000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:noFill/>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="4200" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr>
                                    <a:t>Unit 2.3: Computer Systems &amp; Architecture</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="2200"><a:solidFill><a:srgbClr val="94A3B8"/></a:solidFill></a:rPr>
                                    <a:t>Instruction Pipelines, Cache Coherence &amp; Memory Subsystems</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Author Info Card -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="6" name="AuthorCard"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="4400000"/><a:ext cx="10668000" cy="1200000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="1E293B"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="334155"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1600" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr>
                                    <a:t>Prof. Dr. Sarah Jenkins  •  Department of Computer Science &amp; Engineering</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1300"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr>
                                    <a:t>Spring Semester 2026  •  Lecture Slide Deck (Unit 2.3 - Part 2)</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>
                </p:spTree>
            </p:cSld>
        </p:sld>
    """.trimIndent()

    // Slide 2: Objectives & Agenda
    private fun buildSlide2Xml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>

                    <!-- Slide Title Banner -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="500000"/><a:ext cx="10668000" cy="800000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:noFill/>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="2800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr>
                                    <a:t>Learning Objectives &amp; Session Agenda</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1400"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr>
                                    <a:t>Key architectural mechanisms governing contemporary multi-core processor performance</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Card 1: Pipeline Fundamentals -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="3" name="Card1"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="1600000"/><a:ext cx="5100000" cy="2000000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="E2E8F0"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="2563EB"/></a:solidFill></a:rPr>
                                    <a:t>1. Instruction Pipelining Principles</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="2563EB"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Classic 5-stage RISC pipeline execution cycle</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="2563EB"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Throughput scaling vs. clock frequency constraints</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="2563EB"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Calculating CPI (Cycles Per Instruction) under ideal conditions</a:t></a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Card 2: Hazard Resolution -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="4" name="Card2"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="6300000" y="1600000"/><a:ext cx="5100000" cy="2000000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="E2E8F0"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="0D9488"/></a:solidFill></a:rPr>
                                    <a:t>2. Pipeline Hazard Mitigation</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="0D9488"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Structural hazards: shared resource port contention</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="0D9488"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Data hazards: RAW, WAR, WAW &amp; operand forwarding</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="0D9488"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Control hazards: branch prediction and delayed branching</a:t></a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Card 3: Memory Hierarchy -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="5" name="Card3"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="3800000"/><a:ext cx="5100000" cy="2000000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="E2E8F0"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="F59E0B"/></a:solidFill></a:rPr>
                                    <a:t>3. Memory Hierarchy &amp; Latency</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="F59E0B"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Temporal and spatial locality of reference</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="F59E0B"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>L1, L2, L3 cache organization and hit rate analysis</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="F59E0B"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Main memory DRAM access cycles vs register latency</a:t></a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Card 4: Multi-Core Coherence -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="6" name="Card4"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="6300000" y="3800000"/><a:ext cx="5100000" cy="2000000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="E2E8F0"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="7C3AED"/></a:solidFill></a:rPr>
                                    <a:t>4. Multi-Core Cache Coherence</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="7C3AED"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>MESI (Modified, Exclusive, Shared, Invalid) protocol</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="7C3AED"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Directory-based vs snooping bus architectures</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="•"/><a:buClr><a:srgbClr val="7C3AED"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Memory consistency models &amp; atomic primitives</a:t></a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>
                </p:spTree>
            </p:cSld>
        </p:sld>
    """.trimIndent()

    // Slide 3: 5-Stage RISC Pipeline Architecture Diagram
    private fun buildSlide3Xml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>

                    <!-- Title -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="500000"/><a:ext cx="10668000" cy="800000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:noFill/>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="2800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr>
                                    <a:t>Classic 5-Stage RISC Pipeline Architecture</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1400"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr>
                                    <a:t>Linear pipeline stages operating concurrently across overlapping clock cycles</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Stage 1: IF -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="3" name="StageIF"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="1600000"/><a:ext cx="1900000" cy="2400000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="2563EB"/></a:solidFill>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="2000" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>IF</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="BFDBFE"/></a:solidFill></a:rPr><a:t>Instruction Fetch</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Read from I-Cache</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Increment PC + 4</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Latch into IF/ID</a:t></a:r></a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Stage 2: ID -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="4" name="StageID"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="2950000" y="1600000"/><a:ext cx="1900000" cy="2400000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="0D9488"/></a:solidFill>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="2000" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>ID</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="99F6E4"/></a:solidFill></a:rPr><a:t>Instruction Decode</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Opcode parsing</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Register file read</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Sign extension</a:t></a:r></a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Stage 3: EX -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="5" name="StageEX"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="5140000" y="1600000"/><a:ext cx="1900000" cy="2400000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="D97706"/></a:solidFill>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="2000" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>EX</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="FDE68A"/></a:solidFill></a:rPr><a:t>Execute / ALU</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Arithmetic &amp; logic</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Memory address calc</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Branch target evaluation</a:t></a:r></a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Stage 4: MEM -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="6" name="StageMEM"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="7330000" y="1600000"/><a:ext cx="1900000" cy="2400000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="E11D48"/></a:solidFill>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="2000" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>MEM</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="FECDD3"/></a:solidFill></a:rPr><a:t>Memory Access</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Read data from D-Cache</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Write store operands</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Latch into MEM/WB</a:t></a:r></a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Stage 5: WB -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="7" name="StageWB"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="9520000" y="1600000"/><a:ext cx="1900000" cy="2400000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="7C3AED"/></a:solidFill>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="2000" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>WB</a:t></a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="ctr"/>
                                <a:r><a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="E9D5FF"/></a:solidFill></a:rPr><a:t>Write Back</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Write result to reg</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Update architectural state</a:t></a:r></a:p>
                            <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1100"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>• Instruction complete</a:t></a:r></a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Summary Callout Box -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="8" name="Callout"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="4300000"/><a:ext cx="10668000" cy="1100000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="EFF6FF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="BFDBFE"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="ctr"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="1E40AF"/></a:solidFill></a:rPr>
                                    <a:t>Pipeline Invariant:</a:t>
                                </a:r>
                                <a:r>
                                    <a:rPr sz="1400"><a:solidFill><a:srgbClr val="1E293B"/></a:solidFill></a:rPr>
                                    <a:t> In steady-state operation without stalls, one instruction completes every single clock cycle (CPI = 1.0), yielding a theoretical 5x speedup over unpipelined architectures.</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>
                </p:spTree>
            </p:cSld>
        </p:sld>
    """.trimIndent()

    // Slide 4: Memory Hierarchy Performance Comparison Table
    private fun buildSlide4Xml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>

                    <!-- Title -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="400000"/><a:ext cx="10668000" cy="800000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:noFill/>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="2800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr>
                                    <a:t>Memory Hierarchy Latency &amp; Bandwidth Analysis</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1400"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr>
                                    <a:t>Empirical comparison of access latency, typical storage capacity, and bandwidth scaling</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Table Graphic Frame -->
                    <p:graphicFrame>
                        <p:nvGraphicFramePr><p:cNvPr id="3" name="LatencyTable"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>
                        <p:xfrm><a:off x="762000" y="1400000"/><a:ext cx="10668000" cy="3800000"/></p:xfrm>
                        <a:graphic>
                            <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">
                                <a:tbl>
                                    <a:tblPr/>
                                    <a:tblGrid>
                                        <a:gridCol w="2200000"/>
                                        <a:gridCol w="2100000"/>
                                        <a:gridCol w="2100000"/>
                                        <a:gridCol w="2100000"/>
                                        <a:gridCol w="2168000"/>
                                    </a:tblGrid>

                                    <!-- Header Row -->
                                    <a:tr h="500000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Storage Tier</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Capacity</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Latency (Cycles)</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Bandwidth</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Managed By</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>

                                    <!-- Row 1: Registers -->
                                    <a:tr h="450000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200" b="1"/><a:t>Registers</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>&lt; 1 KB</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200" b="1"><a:solidFill><a:srgbClr val="059669"/></a:solidFill></a:rPr><a:t>0.5 - 1 cycle</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>~1,000 GB/s</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>Compiler</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>

                                    <!-- Row 2: L1 Cache -->
                                    <a:tr h="450000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200" b="1"/><a:t>L1 Data Cache</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>64 KB / core</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200" b="1"><a:solidFill><a:srgbClr val="059669"/></a:solidFill></a:rPr><a:t>4 cycles</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>~400 GB/s</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>Hardware</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>

                                    <!-- Row 3: L2 Cache -->
                                    <a:tr h="450000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200" b="1"/><a:t>L2 Cache</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>512 KB / core</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200" b="1"><a:solidFill><a:srgbClr val="2563EB"/></a:solidFill></a:rPr><a:t>12 cycles</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>~200 GB/s</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>Hardware</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>

                                    <!-- Row 4: L3 Cache -->
                                    <a:tr h="450000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200" b="1"/><a:t>L3 Shared Cache</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>32 MB</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200" b="1"><a:solidFill><a:srgbClr val="F59E0B"/></a:solidFill></a:rPr><a:t>40 cycles</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>~80 GB/s</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>Hardware</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>

                                    <!-- Row 5: Main Memory -->
                                    <a:tr h="450000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200" b="1"/><a:t>Main Memory (DRAM)</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>32 GB</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200" b="1"><a:solidFill><a:srgbClr val="E11D48"/></a:solidFill></a:rPr><a:t>180 - 250 cycles</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>~40 GB/s</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>OS / MMU</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>

                                    <!-- Row 6: NVMe SSD -->
                                    <a:tr h="450000">
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200" b="1"/><a:t>NVMe Flash Storage</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>1 TB</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200" b="1"><a:solidFill><a:srgbClr val="E11D48"/></a:solidFill></a:rPr><a:t>20,000+ cycles</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>~7 GB/s</a:t></a:r></a:p></a:txBody></a:tc>
                                        <a:tc><a:tcPr><a:solidFill><a:srgbClr val="F1F5F9"/></a:solidFill></a:tcPr><a:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1200"/><a:t>OS / File System</a:t></a:r></a:p></a:txBody></a:tc>
                                    </a:tr>
                                </a:tbl>
                            </a:graphicData>
                        </a:graphic>
                    </p:graphicFrame>
                </p:spTree>
            </p:cSld>
        </p:sld>
    """.trimIndent()

    // Slide 5: Embedded Media & Bus Co-Processor Overview
    private fun buildSlide5Xml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>

                    <!-- Slide Title -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="400000"/><a:ext cx="10668000" cy="800000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:noFill/>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="2800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr>
                                    <a:t>High-Speed System Interconnect &amp; Co-Processor Bus</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1400"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr>
                                    <a:t>Hardware block topology uniting heterogeneous compute clusters with low-latency memory</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Architecture Diagram Picture (Left side) -->
                    <p:pic>
                        <p:nvPicPr><p:cNvPr id="3" name="ArchDiagram"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
                        <p:blipFill>
                            <a:blip r:embed="rIdImg2"/>
                            <a:stretch><a:fillRect/></a:stretch>
                        </p:blipFill>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="1400000"/><a:ext cx="5800000" cy="3800000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="CBD5E1"/></a:solidFill></a:ln>
                        </p:spPr>
                    </p:pic>

                    <!-- Descriptive Points Card (Right side) -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="4" name="DescCard"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="6800000" y="1400000"/><a:ext cx="4630000" cy="3800000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="E2E8F0"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr>
                                    <a:t>Architecture Specifications</a:t>
                                </a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="✔"/><a:buClr><a:srgbClr val="059669"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300" b="1"/><a:t>Unified Memory Controller: </a:t></a:r>
                                <a:r><a:rPr sz="1300"/><a:t>Direct zero-copy sharing between CPU and GPU compute queues.</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="✔"/><a:buClr><a:srgbClr val="059669"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300" b="1"/><a:t>Coherent Bus Protocol: </a:t></a:r>
                                <a:r><a:rPr sz="1300"/><a:t>Full hardware cache snooping maintains cache consistency across 8 out-of-order cores.</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="✔"/><a:buClr><a:srgbClr val="059669"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300" b="1"/><a:t>Tensor Engine Interconnect: </a:t></a:r>
                                <a:r><a:rPr sz="1300"/><a:t>Dedicated DMA channel supports 45 TOPS peak inferencing throughput.</a:t></a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>
                </p:spTree>
            </p:cSld>
        </p:sld>
    """.trimIndent()

    // Slide 6: Summary & Review Questions
    private fun buildSlide6Xml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></p:bgPr></p:bg>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>

                    <!-- Slide Title -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="400000"/><a:ext cx="10668000" cy="800000"/></a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:noFill/>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="2800" b="1"><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></a:rPr>
                                    <a:t>Summary &amp; Key Takeaways</a:t>
                                </a:r>
                            </a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1400"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr>
                                    <a:t>Synthesis of pipeline principles, memory hierarchy trade-offs, and upcoming lab preparation</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Takeaways Card (Left) -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="3" name="TakeawaysCard"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="762000" y="1400000"/><a:ext cx="5200000" cy="3800000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="E2E8F0"/></a:solidFill></a:ln>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="2563EB"/></a:solidFill></a:rPr>
                                    <a:t>Core Conceptual Takeaways</a:t>
                                </a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="✔"/><a:buClr><a:srgbClr val="2563EB"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Pipelining increases instruction throughput, not individual instruction execution latency.</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="✔"/><a:buClr><a:srgbClr val="2563EB"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Data hazard forwarding paths bypass the register file to resolve RAW hazards without stalls.</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="✔"/><a:buClr><a:srgbClr val="2563EB"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"/><a:t>Multi-level cache hierarchies bridge the processor-memory performance gap via spatial/temporal locality.</a:t></a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>

                    <!-- Lab Discussion Card (Right) -->
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="4" name="LabCard"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr>
                            <a:xfrm><a:off x="6300000" y="1400000"/><a:ext cx="5130000" cy="3800000"/></a:xfrm>
                            <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
                            <a:solidFill><a:srgbClr val="0F172A"/></a:solidFill>
                        </p:spPr>
                        <p:txBody>
                            <a:bodyPr anchor="t"/>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="F59E0B"/></a:solidFill></a:rPr>
                                    <a:t>Review Questions for Lab 3</a:t>
                                </a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="Q1:"/><a:buClr><a:srgbClr val="F59E0B"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t> How does a 2-bit saturating branch predictor overcome simple 1-bit predictor hysteresis?</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"><a:buChar char="Q2:"/><a:buClr><a:srgbClr val="F59E0B"/></a:buClr></a:pPr>
                                <a:r><a:rPr sz="1300"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t> In the MESI protocol, what transition occurs when a core issues a read request to a line in Shared state?</a:t></a:r>
                            </a:p>
                            <a:p><a:pPr algn="l"/><a:r><a:rPr sz="1200"/><a:t> </a:t></a:r></a:p>
                            <a:p>
                                <a:pPr algn="l"/>
                                <a:r>
                                    <a:rPr sz="1200" i="1"><a:solidFill><a:srgbClr val="94A3B8"/></a:solidFill></a:rPr>
                                    <a:t>Next Session: Unit 2.4 Multicore Synchronization &amp; Lock-Free Queues</a:t>
                                </a:r>
                            </a:p>
                        </p:txBody>
                    </p:sp>
                </p:spTree>
            </p:cSld>
        </p:sld>
    """.trimIndent()
}
