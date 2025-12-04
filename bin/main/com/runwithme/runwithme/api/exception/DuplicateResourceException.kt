package com.runwithme.runwithme.api.exception

class DuplicateResourceException(
    val field: String,
    message: String,
) : RuntimeException(message)
