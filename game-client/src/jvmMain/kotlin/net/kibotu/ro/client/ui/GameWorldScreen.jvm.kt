package net.kibotu.ro.client.ui

import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger
import net.kibotu.ro.client.viewmodel.GameViewModel

/**
 * JVM-specific implementation of LibGDXWindowRef
 */
actual class LibGDXWindowRef(private val window: LibGDXWindow) {
    actual fun close() {
        window.close()
    }
}

/**
 * JVM-specific implementation: Launch LibGDX window
 */
actual fun launchLibGDXGameWindow(
    viewModel: GameViewModel,
    characterId: String,
    initialPosition: Position
): LibGDXWindowRef {
    ClientLogger.info("launchLibGDXGameWindow called: characterId=$characterId, position=$initialPosition", "GameWorldScreen")
    val libGDXWindow = LibGDXWindow(
        viewModel = viewModel,
        ownCharacterId = characterId,
        initialPosition = initialPosition
    )
    libGDXWindow.show()
    return LibGDXWindowRef(libGDXWindow)
}

