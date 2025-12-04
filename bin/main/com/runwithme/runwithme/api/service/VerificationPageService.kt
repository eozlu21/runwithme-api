package com.runwithme.runwithme.api.service

import org.springframework.stereotype.Service

@Service
class VerificationPageService {
    fun buildVerificationHtml(
        success: Boolean,
        message: String,
    ): String {
        val icon = if (success) "✓" else "✗"
        val iconColor = if (success) "#4CAF50" else "#f44336"
        val title = if (success) "Email Verified!" else "Verification Failed"

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title - RunWithMe</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen,
                            Ubuntu, Cantarell, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .card {
                        background: white;
                        border-radius: 16px;
                        padding: 48px;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
                        max-width: 420px;
                        width: 100%;
                    }
                    .icon {
                        width: 80px;
                        height: 80px;
                        border-radius: 50%;
                        background: $iconColor;
                        color: white;
                        font-size: 40px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 24px;
                    }
                    h1 {
                        color: #333;
                        font-size: 28px;
                        margin-bottom: 16px;
                    }
                    p {
                        color: #666;
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 32px;
                    }
                    .logo {
                        font-size: 24px;
                        font-weight: bold;
                        color: #667eea;
                        margin-bottom: 32px;
                    }
                    .footer {
                        color: #999;
                        font-size: 14px;
                        margin-top: 24px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo">🏃 RunWithMe</div>
                    <div class="icon">$icon</div>
                    <h1>$title</h1>
                    <p>$message</p>
                    <div class="footer">You can close this page now.</div>
                </div>
            </body>
            </html>
            """.trimIndent()
    }
}
