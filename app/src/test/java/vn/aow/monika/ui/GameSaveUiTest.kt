package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.screens.GameSaveButtons
import vn.aow.monika.ui.theme.MonikaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=TestApp::class,sdk=[34],qualifiers="w393dp-h851dp-xxhdpi")
class GameSaveUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun twoSaveActionsUseMonikaAndDoNotLaunchGameOrDeleteAnything() {
        var exported=0;var imported=0
        rule.mainClock.autoAdvance=false
        rule.setContent { MonikaTheme { Column(Modifier.fillMaxWidth()) {
            GameSaveButtons(true,false,{exported++},{imported++})
        } } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Xuất save").assertIsDisplayed().performClick()
        rule.onNodeWithText("Nhập save").assertIsDisplayed().performClick()
        assertEquals(1,exported);assertEquals(1,imported)
        rule.onNodeWithText("Chơi").assertDoesNotExist();rule.onNodeWithText("Xóa game").assertDoesNotExist()
        rule.shot("v134-hai-nut-chuyen-save")
    }
}
