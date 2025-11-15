package net.kibotu.ro

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform