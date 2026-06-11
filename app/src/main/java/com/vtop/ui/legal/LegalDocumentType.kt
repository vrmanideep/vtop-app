package com.vtop.ui.legal

enum class LegalDocumentType(
    val title: String,
    val assetFile: String
) {

    PRIVACY_POLICY(
        "Privacy Policy",
        "privacy_policy.md"
    ),

    TERMS_OF_USE(
        "Terms of Use",
        "terms_of_use.md"
    ),

    DISCLAIMER(
        "Disclaimer",
        "disclaimer.md"
    ),

    OPEN_SOURCE(
        "Open Source Licenses",
        "open_source_licenses.md"
    ),

    CHANGELOG(
        "What's New",
        "changelog.md"
    )
}