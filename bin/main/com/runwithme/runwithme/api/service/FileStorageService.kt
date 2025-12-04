package com.runwithme.runwithme.api.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class FileStorageService(
    @Value("\${file.upload-dir:uploads}") private val uploadDir: String,
) {
    private val rootLocation: Path = Paths.get(uploadDir)

    init {
        try {
            Files.createDirectories(rootLocation)
        } catch (e: IOException) {
            throw RuntimeException("Could not initialize storage", e)
        }
    }

    fun store(file: MultipartFile): String {
        try {
            if (file.isEmpty) {
                throw RuntimeException("Failed to store empty file.")
            }
            val originalFilename = file.originalFilename ?: "unknown.jpg"
            val extension = originalFilename.substringAfterLast('.', "jpg")
            val filename = "${UUID.randomUUID()}.$extension"
            val destinationFile =
                this.rootLocation
                    .resolve(Paths.get(filename))
                    .normalize()
                    .toAbsolutePath()

            if (!destinationFile.parent.equals(this.rootLocation.toAbsolutePath())) {
                // This is a security check
                throw RuntimeException("Cannot store file outside current directory.")
            }

            Files.copy(file.inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING)
            return filename
        } catch (e: IOException) {
            throw RuntimeException("Failed to store file.", e)
        }
    }

    fun load(filename: String): Path = rootLocation.resolve(filename)
}
