package com.runwithme.runwithme.api.exception

class EmailNotVerifiedException(
    message: String =
        "Email address has not been verified. Please check your email for a verification link.",
) : RuntimeException(message)
