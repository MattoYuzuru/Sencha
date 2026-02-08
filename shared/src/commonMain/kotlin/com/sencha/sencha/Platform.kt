package com.sencha.sencha

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform