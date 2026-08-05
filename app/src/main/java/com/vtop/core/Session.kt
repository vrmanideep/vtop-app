package com.vtop.core

import com.vtop.network.VtopClient

data class Session(
    val client: VtopClient,
    val username: String,
    val authorizedId: String,
    val semesterId: String
)