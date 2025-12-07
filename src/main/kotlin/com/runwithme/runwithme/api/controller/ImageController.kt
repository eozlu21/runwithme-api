package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.service.S3StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.security.Principal

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Images", description = "Image upload and retrieval APIs for Lightsail bucket")
class ImageController(
    private val s3StorageService: S3StorageService,
) {
    @GetMapping("/profile-pictures/{filename}")
    @Operation(
        summary = "Get profile picture",
        description = "Retrieves a profile picture from the Lightsail bucket by filename",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Image retrieved successfully"),
            ApiResponse(responseCode = "404", description = "Image not found"),
        ],
    )
    fun getProfilePicture(
        @Parameter(description = "Filename of the profile picture")
        @PathVariable filename: String,
    ): ResponseEntity<InputStreamResource> {
        val key = "profile-pictures/$filename"
        val result =
            s3StorageService.downloadFile(key)
                ?: return ResponseEntity.notFound().build()

        val (inputStream, contentType) = result

        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(InputStreamResource(inputStream))
    }

    @GetMapping("/{folder}/{filename}")
    @Operation(
        summary = "Get image from folder",
        description = "Retrieves an image from a specific folder in the Lightsail bucket",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Image retrieved successfully"),
            ApiResponse(responseCode = "404", description = "Image not found"),
        ],
    )
    fun getImage(
        @Parameter(description = "Folder name")
        @PathVariable folder: String,
        @Parameter(description = "Filename of the image")
        @PathVariable filename: String,
    ): ResponseEntity<InputStreamResource> {
        val key = "$folder/$filename"
        val result =
            s3StorageService.downloadFile(key)
                ?: return ResponseEntity.notFound().build()

        val (inputStream, contentType) = result

        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(InputStreamResource(inputStream))
    }

    @PostMapping("/profile-pictures", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload profile picture",
        description = "Uploads a profile picture to the Lightsail bucket",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Image uploaded successfully"),
            ApiResponse(responseCode = "400", description = "Invalid file or upload failed"),
        ],
    )
    fun uploadProfilePicture(
        @Parameter(description = "Image file to upload")
        @RequestParam("file") file: MultipartFile,
        principal: Principal,
    ): ResponseEntity<Map<String, String>> {
        if (file.isEmpty) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to "File is empty"))
        }

        // Validate file type
        val contentType = file.contentType ?: ""
        if (!contentType.startsWith("image/")) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to "Only image files are allowed"))
        }

        return try {
            val key = s3StorageService.uploadFile(file, "profile-pictures")
            val filename = key.substringAfterLast("/")
            val url = "/api/v1/images/profile-pictures/$filename"

            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    mapOf(
                        "key" to key,
                        "filename" to filename,
                        "url" to url,
                    ),
                )
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to upload file: ${e.message}"))
        }
    }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload image to specified folder",
        description = "Uploads an image to a specified folder in the Lightsail bucket",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Image uploaded successfully"),
            ApiResponse(responseCode = "400", description = "Invalid file or upload failed"),
        ],
    )
    fun uploadImage(
        @Parameter(description = "Image file to upload")
        @RequestParam("file") file: MultipartFile,
        @Parameter(description = "Folder to upload the image to")
        @RequestParam("folder", required = false, defaultValue = "") folder: String,
        principal: Principal,
    ): ResponseEntity<Map<String, String>> {
        if (file.isEmpty) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to "File is empty"))
        }

        // Validate file type
        val contentType = file.contentType ?: ""
        if (!contentType.startsWith("image/")) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to "Only image files are allowed"))
        }

        return try {
            val key = s3StorageService.uploadFile(file, folder)
            val filename = key.substringAfterLast("/")
            val url =
                if (folder.isNotBlank()) {
                    "/api/v1/images/$folder/$filename"
                } else {
                    "/api/v1/images/$filename"
                }

            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    mapOf(
                        "key" to key,
                        "filename" to filename,
                        "url" to url,
                    ),
                )
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to upload file: ${e.message}"))
        }
    }

    @DeleteMapping("/{folder}/{filename}")
    @Operation(
        summary = "Delete image",
        description = "Deletes an image from the Lightsail bucket",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Image deleted successfully"),
            ApiResponse(responseCode = "404", description = "Image not found"),
        ],
    )
    fun deleteImage(
        @Parameter(description = "Folder name")
        @PathVariable folder: String,
        @Parameter(description = "Filename of the image")
        @PathVariable filename: String,
        principal: Principal,
    ): ResponseEntity<Void> {
        val key = "$folder/$filename"

        if (!s3StorageService.fileExists(key)) {
            return ResponseEntity.notFound().build()
        }

        s3StorageService.deleteFile(key)
        return ResponseEntity.noContent().build()
    }
}
