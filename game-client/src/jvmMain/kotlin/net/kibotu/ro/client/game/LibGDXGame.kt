package net.kibotu.ro.client.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import net.kibotu.ro.client.util.ClientLogger

/**
 * LibGDX ApplicationAdapter that manages the game screen.
 * This is the main entry point for the LibGDX game loop.
 */
class LibGDXGame(
    private val gameScreen: GameScreen
) : ApplicationAdapter() {
    
    private var currentScreen: GameScreen? = gameScreen
    
    override fun create() {
        ClientLogger.info("LibGDXGame.create() called - Initializing LibGDX", "LibGDXGame")
        ClientLogger.info("Graphics: ${Gdx.graphics.width}x${Gdx.graphics.height}", "LibGDXGame")
        
        // Set input processor for keyboard and mouse handling
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                val handled = currentScreen?.handleKeyDown(keycode) ?: false
                if (!handled) {
                    ClientLogger.debug("KeyDown not handled: keycode=$keycode", "LibGDXGame")
                }
                return handled
            }
            
            override fun keyUp(keycode: Int): Boolean {
                val handled = currentScreen?.handleKeyUp(keycode) ?: false
                if (!handled) {
                    ClientLogger.debug("KeyUp not handled: keycode=$keycode", "LibGDXGame")
                }
                return handled
            }
            
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val handled = currentScreen?.handleMouseDown(screenX, screenY, button) ?: false
                if (handled) {
                    ClientLogger.debug("MouseDown handled: screenX=$screenX, screenY=$screenY, button=$button", "LibGDXGame")
                }
                return handled
            }
            
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val handled = currentScreen?.handleMouseUp(screenX, screenY, button) ?: false
                if (handled) {
                    ClientLogger.debug("MouseUp handled: screenX=$screenX, screenY=$screenY, button=$button", "LibGDXGame")
                }
                return handled
            }
            
            override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
                val handled = currentScreen?.handleMouseMoved(screenX, screenY) ?: false
                return handled
            }
            
            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                val deltaTime = Gdx.graphics.deltaTime
                val handled = currentScreen?.handleMouseDragged(screenX, screenY, deltaTime) ?: false
                if (handled) {
                    ClientLogger.debug("MouseDragged handled: screenX=$screenX, screenY=$screenY", "LibGDXGame")
                }
                return handled
            }
            
            override fun scrolled(amountX: Float, amountY: Float): Boolean {
                val handled = currentScreen?.handleScrolled(amountX, amountY) ?: false
                return handled
            }
        }
        
        ClientLogger.info("Input processor set", "LibGDXGame")
        
        // Show the initial screen
        currentScreen?.show()
        ClientLogger.info("LibGDXGame.create() completed", "LibGDXGame")
    }
    
    override fun render() {
        currentScreen?.render(Gdx.graphics.deltaTime)
    }
    
    override fun resize(width: Int, height: Int) {
        currentScreen?.resize(width, height)
    }
    
    override fun dispose() {
        currentScreen?.dispose()
    }
    
    /**
     * Switch to a different game screen (for future use)
     */
    fun setScreen(screen: GameScreen) {
        currentScreen?.hide()
        currentScreen?.dispose()
        currentScreen = screen
        screen.show()
        screen.resize(Gdx.graphics.width, Gdx.graphics.height)
    }
}

