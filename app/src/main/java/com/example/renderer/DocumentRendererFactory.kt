package com.example.renderer

import com.example.domain.model.DocumentType

class DocumentRendererFactory {

    fun createRenderer(type: DocumentType): DocumentRenderer {
        return when (type) {
            DocumentType.PDF -> PdfRenderer()
            DocumentType.WORD -> DocxRenderer()
            DocumentType.POWERPOINT -> PptxRenderer()
            DocumentType.EXCEL -> XlsxRenderer()
            DocumentType.TEXT -> TextRenderer()
            DocumentType.IMAGE -> ImageRenderer()
            DocumentType.UNSUPPORTED -> UnsupportedRenderer()
        }
    }
}
