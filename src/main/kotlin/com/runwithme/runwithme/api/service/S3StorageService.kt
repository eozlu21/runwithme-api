package com.runwithme.runwithme.api.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream
import java.net.URI
import java.util.UUID

@Service
class S3StorageService(
    @Value("\${aws.lightsail.bucket.name}") private val bucketName: String,
    @Value("\${aws.lightsail.bucket.region}") private val region: String,
    @Value("\${aws.lightsail.bucket.access-key}") private val accessKey: String,
    @Value("\${aws.lightsail.bucket.secret-key}") private val secretKey: String,
    @Value("\${aws.lightsail.bucket.endpoint:}") private val endpoint: String,
) {
    private val s3Client: S3Client by lazy {
        val builder =
            S3Client
                .builder()
                .region(Region.of(region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey),
                    ),
                )

        // Set custom endpoint for Lightsail bucket if provided
        if (endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(endpoint))
        }

        builder.build()
    }

    /**
     * Upload a file to the S3/Lightsail bucket.
     *
     * @param file The multipart file to upload
     * @param folder Optional folder path within the bucket (e.g., "profile-pictures")
     * @return The key (path) of the uploaded file in the bucket
     */
    fun uploadFile(
        file: MultipartFile,
        folder: String = "",
    ): String {
        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast('.', "jpg")
        val filename = "${UUID.randomUUID()}.$extension"
        val key = if (folder.isNotBlank()) "$folder/$filename" else filename

        val putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.contentType ?: "application/octet-stream")
                .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.inputStream, file.size))

        return key
    }

    /**
     * Upload a file from an input stream to the S3/Lightsail bucket.
     *
     * @param inputStream The input stream of the file
     * @param contentLength The length of the content
     * @param contentType The MIME type of the content
     * @param folder Optional folder path within the bucket
     * @param extension File extension (default: "jpg")
     * @return The key (path) of the uploaded file in the bucket
     */
    fun uploadFile(
        inputStream: InputStream,
        contentLength: Long,
        contentType: String,
        folder: String = "",
        extension: String = "jpg",
    ): String {
        val filename = "${UUID.randomUUID()}.$extension"
        val key = if (folder.isNotBlank()) "$folder/$filename" else filename

        val putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength))

        return key
    }

    /**
     * Download a file from the S3/Lightsail bucket.
     *
     * @param key The key (path) of the file in the bucket
     * @return A pair of the input stream and content type, or null if the file doesn't exist
     */
    fun downloadFile(key: String): Pair<InputStream, String>? =
        try {
            val getObjectRequest =
                GetObjectRequest
                    .builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()

            val response = s3Client.getObject(getObjectRequest)
            val contentType = response.response().contentType() ?: "application/octet-stream"
            Pair(response, contentType)
        } catch (e: NoSuchKeyException) {
            null
        }

    /**
     * Delete a file from the S3/Lightsail bucket.
     *
     * @param key The key (path) of the file to delete
     */
    fun deleteFile(key: String) {
        val deleteObjectRequest =
            DeleteObjectRequest
                .builder()
                .bucket(bucketName)
                .key(key)
                .build()

        s3Client.deleteObject(deleteObjectRequest)
    }

    /**
     * Check if a file exists in the S3/Lightsail bucket.
     *
     * @param key The key (path) of the file to check
     * @return True if the file exists, false otherwise
     */
    fun fileExists(key: String): Boolean =
        try {
            val headObjectRequest =
                HeadObjectRequest
                    .builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()

            s3Client.headObject(headObjectRequest)
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    /**
     * Get the public URL for an object in the bucket.
     * Note: This only works if the bucket/object has public access enabled.
     *
     * @param key The key (path) of the file
     * @return The public URL of the file
     */
    fun getPublicUrl(key: String): String =
        if (endpoint.isNotBlank()) {
            "$endpoint/$key"
        } else {
            "https://$bucketName.s3.$region.amazonaws.com/$key"
        }
}
