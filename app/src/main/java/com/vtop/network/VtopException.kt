package com.vtop.network

open class VtopException(message: String) : Exception(message) {
    class InvalidCredentials(msg: String = "Invalid username or password.") : VtopException(msg)
    class AuthenticationFailed(msg: String = "Account temporarily locked or auth failed.") : VtopException(msg)
    class LoginOtpRequired(msg: String = "OTP required for login.") : VtopException(msg)
    class LoginOtpIncorrect(msg: String = "OTP is incorrect or expired.") : VtopException(msg)
    class SessionExpired(msg: String = "Session expired or network timeout.") : VtopException(msg)
    class CaptchaFailed(msg: String = "Captcha verification failed.") : VtopException(msg)
    class WafBlocked(msg: String = "Session blocked by VTOP Firewall.") : VtopException(msg)
}