package br.com.finsavior.monolith.finsavior_monolith.util

import org.springframework.web.multipart.MultipartFile
import java.io.File

/**
 * Validates AI document and audio uploads before processing.
 * Aligns with [spring.servlet.multipart.max-file-size] (10 MB).
 */
object MediaUploadValidator {

    const val MAX_PDF_SIZE_BYTES = 10L * 1024 * 1024
    const val MAX_AUDIO_SIZE_BYTES = 10L * 1024 * 1024
    const val MAX_PDF_EXTRACTED_TEXT_CHARS = 250_000

    private val ALLOWED_PDF_CONTENT_TYPES = setOf(
        "application/pdf",
        "application/x-pdf",
    )

    private val ALLOWED_AUDIO_CONTENT_TYPES = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/x-m4a",
        "audio/m4a",
        "audio/wav",
        "audio/x-wav",
        "audio/webm",
        "audio/ogg",
        "audio/aac",
        "video/webm",
        "application/octet-stream",
    )

    private val ALLOWED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "mp4", "wav", "webm", "ogg", "aac", "3gp", "amr",
    )

    fun validatePdfUpload(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("Arquivo PDF vazio.")
        }
        if (file.size > MAX_PDF_SIZE_BYTES) {
            throw IllegalArgumentException("PDF excede o tamanho máximo de 10 MB.")
        }

        val contentType = file.contentType?.lowercase()
        if (contentType != null && contentType !in ALLOWED_PDF_CONTENT_TYPES) {
            throw IllegalArgumentException("Formato inválido. Envie um arquivo PDF.")
        }

        val header = readHeader(file.bytes)
        if (!hasPdfSignature(header)) {
            throw IllegalArgumentException("Arquivo inválido ou corrompido. Envie um PDF válido.")
        }
    }

    fun validateAudioUpload(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("Arquivo de áudio vazio.")
        }
        validateAudioSize(file.size)
        validateAudioContent(file.contentType, file.originalFilename, readHeader(file.bytes))
    }

    fun validateAudioFile(file: File) {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("Arquivo de áudio vazio.")
        }
        validateAudioSize(file.length())
        validateAudioContent(null, file.name, readHeader(file.readBytes()))
    }

    fun validateExtractedPdfTextLength(text: String) {
        if (text.length > MAX_PDF_EXTRACTED_TEXT_CHARS) {
            throw IllegalArgumentException(
                "PDF muito extenso para processamento (${text.length} caracteres). " +
                    "Use um extrato menor ou divida o documento."
            )
        }
    }

    private fun validateAudioSize(sizeBytes: Long) {
        if (sizeBytes > MAX_AUDIO_SIZE_BYTES) {
            throw IllegalArgumentException("Áudio excede o tamanho máximo de 10 MB.")
        }
    }

    private fun validateAudioContent(contentType: String?, originalFilename: String?, header: ByteArray) {
        val normalizedContentType = contentType?.lowercase()
        val extension = originalFilename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        val contentTypeAllowed = normalizedContentType == null ||
            normalizedContentType in ALLOWED_AUDIO_CONTENT_TYPES
        val extensionAllowed = extension == null || extension in ALLOWED_AUDIO_EXTENSIONS

        if (!contentTypeAllowed && !extensionAllowed) {
            throw IllegalArgumentException("Formato de áudio inválido. Use MP3, M4A, WAV, WEBM ou OGG.")
        }

        if (!hasAudioSignature(header)) {
            throw IllegalArgumentException("Arquivo de áudio inválido ou corrompido.")
        }
    }

    private fun readHeader(bytes: ByteArray, length: Int = 16): ByteArray =
        bytes.copyOfRange(0, minOf(bytes.size, length))

    private fun hasPdfSignature(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == '%'.code.toByte() &&
            header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() &&
            header[3] == 'F'.code.toByte()
    }

    private fun hasAudioSignature(header: ByteArray): Boolean {
        if (header.size < 4) return false
        fun u(i: Int) = header[i].toInt() and 0xFF

        val isMp3 = (u(0) == 0x49 && u(1) == 0x44 && u(2) == 0x33) || // ID3
            (u(0) == 0xFF && u(1) in 0xE0..0xFF)
        val isWav = u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 &&
            header.size >= 12 &&
            u(8) == 0x57 && u(9) == 0x41 && u(10) == 0x56 && u(11) == 0x45
        val isOgg = u(0) == 0x4F && u(1) == 0x67 && u(2) == 0x67 && u(3) == 0x53
        val isWebm = u(0) == 0x1A && u(1) == 0x45 && u(2) == 0xDF && u(3) == 0xA3
        val isMp4 = header.size >= 12 &&
            u(4) == 0x66 && u(5) == 0x74 && u(6) == 0x79 && u(7) == 0x70 // ftyp

        return isMp3 || isWav || isOgg || isWebm || isMp4
    }
}
