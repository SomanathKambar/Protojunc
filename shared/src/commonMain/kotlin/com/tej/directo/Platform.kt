package com.tej.directo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform