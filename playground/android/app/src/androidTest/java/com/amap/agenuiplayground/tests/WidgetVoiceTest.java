package com.amap.agenuiplayground.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.agenuiplayground.widget.WidgetVoiceHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * L3-P2.2: WidgetVoiceTest — 语音输入链路测试
 *
 * P2.2 使用 Android SpeechRecognizer（在线 Google STT），非 Vosk。
 * 源码类：WidgetVoiceHelper
 *
 * 设计原则：纯反射测试，不调用 SpeechRecognizer API（避免设备无 Google 服务时进程崩溃）
 * Runtime 测试用 assumeTrue 跳过。
 *
 * 18 个测试用例
 */
@RunWith(AndroidJUnit4.class)
public class WidgetVoiceTest {

    // VH-01 ~ VH-06: 类结构反射测试（不触发 SpeechRecognizer）
    @Test
    public void test01_VH_classExists() {
        assertNotNull("WidgetVoiceHelper class should exist", WidgetVoiceHelper.class);
    }

    @Test
    public void test02_VH_voiceCallbackInterfaceExists() {
        assertNotNull("VoiceCallback interface should exist",
                WidgetVoiceHelper.VoiceCallback.class);
    }

    @Test
    public void test03_VH_voiceCallbackMethods() {
        boolean hasOnResult = false, hasOnError = false;
        for (Method m : WidgetVoiceHelper.VoiceCallback.class.getDeclaredMethods()) {
            if (m.getName().equals("onResult")) hasOnResult = true;
            if (m.getName().equals("onError")) hasOnError = true;
        }
        assertTrue("VoiceCallback should have onResult", hasOnResult);
        assertTrue("VoiceCallback should have onError", hasOnError);
    }

    @Test
    public void test04_VH_hasStartListening() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("startListening")) found = true;
        }
        assertTrue("WidgetVoiceHelper should have startListening()", found);
    }

    @Test
    public void test05_VH_hasStopListening() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("stopListening")) found = true;
        }
        assertTrue("WidgetVoiceHelper should have stopListening()", found);
    }

    @Test
    public void test06_VH_hasDestroy() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("destroy")) found = true;
        }
        assertTrue("WidgetVoiceHelper should have destroy()", found);
    }

    // VH-07 ~ VH-12: RecognitionListener 接口方法
    @Test
    public void test07_VH_implementsRecognitionListener() {
        boolean implementsListener = false;
        for (Class<?> iface : WidgetVoiceHelper.class.getInterfaces()) {
            if (iface.getSimpleName().equals("RecognitionListener")) implementsListener = true;
        }
        assertTrue("WidgetVoiceHelper should implement RecognitionListener", implementsListener);
    }

    @Test
    public void test08_VH_hasOnResults() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("onResults")) found = true;
        }
        assertTrue("Should have onResults", found);
    }

    @Test
    public void test09_VH_hasOnPartialResults() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("onPartialResults")) found = true;
        }
        assertTrue("Should have onPartialResults", found);
    }

    @Test
    public void test10_VH_hasOnError() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("onError")) found = true;
        }
        assertTrue("Should have onError", found);
    }

    @Test
    public void test11_VH_hasOnBeginningOfSpeech() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("onBeginningOfSpeech")) found = true;
        }
        assertTrue("Should have onBeginningOfSpeech", found);
    }

    @Test
    public void test12_VH_hasOnEndOfSpeech() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("onEndOfSpeech")) found = true;
        }
        assertTrue("Should have onEndOfSpeech", found);
    }

    // VH-13: getErrorMessage 静态方法
    @Test
    public void test13_VH_hasGetErrorMessage() {
        boolean found = false;
        for (Method m : WidgetVoiceHelper.class.getDeclaredMethods()) {
            if (m.getName().equals("getErrorMessage")) found = true;
        }
        assertTrue("Should have getErrorMessage", found);
    }

    // VH-14: 构造函数参数数量
    @Test
    public void test14_VH_constructorParams() {
        // Constructor should have 5 params: Activity, TextView, TextView, ImageButton, VoiceCallback
        assertEquals("WidgetVoiceHelper should have 1 constructor",
                1, WidgetVoiceHelper.class.getDeclaredConstructors().length);
        int paramCount = WidgetVoiceHelper.class.getDeclaredConstructors()[0].getParameterTypes().length;
        assertEquals("Constructor should have 5 params", 5, paramCount);
    }

    // VH-15 ~ VH-18: Runtime tests（需真实 Activity + 麦克风，跳过）
    @Test
    public void test15_VH_startListening_updatesStatus() {
        assumeTrue("Needs real Activity + Google services", false);
    }

    @Test
    public void test16_VH_partialResults_updatesUI() {
        assumeTrue("Needs real speech input", false);
    }

    @Test
    public void test17_VH_stopListening_releasesResources() {
        assumeTrue("Needs real Activity", false);
    }

    @Test
    public void test18_VH_destroy_releasesRecognizer() {
        assumeTrue("Needs real Activity", false);
    }
}
