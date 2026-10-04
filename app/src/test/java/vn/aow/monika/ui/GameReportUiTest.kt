package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.theme.MonikaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=TestApp::class,sdk=[34],qualifiers="w393dp-h851dp-xxhdpi")
class GameReportUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @org.junit.Before fun manualClock() { rule.mainClock.autoAdvance = false }
    @Test fun typeOptionalDescriptionAndDefaultImageReachSubmit() {
        val image=Diagnostics.ReportImage("image/jpeg","fixture",24,16)
        var submitted:Triple<String,String,Diagnostics.ReportImage?>?=null
        rule.setContent { MonikaTheme { GameReportDialog(image,false,{}) { a,b,c -> submitted=Triple(a,b,c) } } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Không lên hình").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Mô tả thêm (tùy chọn)").performTextInput("màn đen")
        rule.onNodeWithText("Gửi báo lỗi").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertEquals(Triple("Không lên hình","màn đen",image),submitted) }
    }
    @Test fun imageCanBeDisabledAndDescriptionIsOptional() {
        val image=Diagnostics.ReportImage("image/jpeg","fixture",24,16)
        var submitted:Triple<String,String,Diagnostics.ReportImage?>?=null
        rule.setContent { MonikaTheme { GameReportDialog(image,false,{}) { a,b,c -> submitted=Triple(a,b,c) } } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNode(isToggleable()).performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Gửi báo lỗi").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertEquals(Triple("Lỗi khác","",null),submitted) }
    }
}
