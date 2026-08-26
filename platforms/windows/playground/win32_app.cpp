// AGenUI Windows Playground — Phase 3: Multi-scenario A2UI protocol tests
//
// Phase 3.3: Multiple preset scenarios switchable via F2/F3/F4/F5.
// Each scenario sends a complete A2UI JSON stream (createSurface +
// updateComponents) to exercise different layout combinations:
//   F2: Column root → Text(title/subtitle/body) + Button + Image
//   F3: Row root → Text(left) + Button(center) + Text(right)
//   F4: Column root → Row(middle) → [Text(a), Button(b), Text(c)]
//   F5: Column root → Text(title) + 3 Buttons (different colors/radii)
//
// 1. Creates a Win32 window (per-Monitor DPI aware)
// 2. Initializes Direct2D HwndRenderTarget
// 3. Initializes AGenUI engine + SurfaceManager
// 4. Auto-timer (1.5s) sends scenario 1; F2/F3/F4/F5 switch scenarios
// 5. Listener captures all component types + layout coordinates
// 6. Direct2D renders each component at its Yoga-computed position
//    (x*2, y*2 converts a2ui logical units to DIP for Direct2D)
//
// Build: see platforms/windows/playground/CMakeLists.txt

#include "agenui_windows_entry.h"
#include "agenui_surface_manager_interface.h"
#include "agenui_engine.h"

#include "d2d_resources.h"
#include "win_surface_size_provider.h"
#include "win_platform_function.h"
#include "win_message_listener.h"
#include "win_wic_image_loader.h"

#include <windows.h>
#include <windowsx.h>
#include <d2d1.h>
#include <dwrite.h>
#include <wrl/client.h>

#include <cstdio>
#include <string>
#include <memory>

// ---------------------------------------------------------------------------
// Utility: parse hex color string ("#RRGGBB" or "#RRGGBBBAA") to D2D1_COLOR_F
// ---------------------------------------------------------------------------
static D2D1_COLOR_F ParseHexColor(const std::string& hex, D2D1_COLOR_F defaultColor) {
    if (hex.empty() || hex[0] != '#') return defaultColor;
    std::string h = hex.substr(1);
    if (h.length() == 6) {
        // #RRGGBB
        unsigned int r, g, b;
        if (sscanf(h.c_str(), "%02x%02x%02x", &r, &g, &b) == 3) {
            return D2D1::ColorF(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
        }
    } else if (h.length() == 8) {
        // #RRGGBBAA
        unsigned int r, g, b, a;
        if (sscanf(h.c_str(), "%02x%02x%02x%02x", &r, &g, &b, &a) == 4) {
            return D2D1::ColorF(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
        }
    }
    return defaultColor;
}

// Convert UTF-8 to wide string
static std::wstring ToWide(const std::string& utf8) {
    if (utf8.empty()) return L"";
    int wlen = MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, nullptr, 0);
    std::wstring wstr(wlen, 0);
    MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, &wstr[0], wlen);
    if (!wstr.empty() && wstr.back() == L'\0') wstr.pop_back();
    return wstr;
}

// ---------------------------------------------------------------------------
// Globals
// ---------------------------------------------------------------------------
static HWND                      g_hwnd = nullptr;
static ID2D1HwndRenderTarget*     g_renderTarget = nullptr;
static IDWriteTextFormat*          g_defaultTextFormat = nullptr;
static ID2D1SolidColorBrush*       g_textBrush = nullptr;

static agenui::IAGenUIEngine*     g_engine = nullptr;
static agenui::ISurfaceManager*   g_surfaceMgr = nullptr;
static agenui_win::WindowsMessageListener g_listener;
static agenui_win::WindowsSurfaceSizeProvider* g_sizeProvider = nullptr;
static agenui_win::WindowsPlatformFunction   g_platformFunc;

// Phase 3.1: WIC image loader (caches ID2D1Bitmap per file path)
static agenui_win::WicImageLoader g_imageLoader;

static const wchar_t* kWindowTitle = L"AGenUI Windows Playground - Phase 3";
static const int kWindowWidth  = 800;
static const int kWindowHeight = 600;

// Phase 3.2: transient click highlight state.
// Set on WM_LBUTTONDOWN hit, fades after a short timer. The renderer tints
// the clicked button green while active so the user gets visual feedback.
static std::string g_highlightId;        // component id currently highlighted
static ULONGLONG   g_highlightStartMs = 0;
static const DWORD kHighlightDurationMs = 400;

// Phase 3.2: cached hand-cursor for button hover.
static HCURSOR g_handCursor = nullptr;
static bool   g_isHoveringButton = false;

// Phase 3.3: initial scenario to auto-run (set from AGENUI_SCENARIO env var).
static int g_initialScenario = 1;

// ---------------------------------------------------------------------------
// Forward declarations
// ---------------------------------------------------------------------------
LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM);
static bool CreateDeviceResources();
static void DiscardDeviceResources();
static void OnRender();
static void OnSize(UINT width, UINT height);
static bool InitAGenUI();
static void ShutdownAGenUI();

// Phase 3.2: hit-test captured components and route a click action.
static void HandleLButtonDown(int pixelX, int pixelY);

// ---------------------------------------------------------------------------
// WinMain
// ---------------------------------------------------------------------------
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE, PWSTR, int nCmdShow) {
    // Per-Monitor DPI awareness
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);

    // Register window class
    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = nullptr;
    wc.lpszClassName = L"AGenUIPlayground";
    RegisterClassExW(&wc);

    // Create window
    g_hwnd = CreateWindowExW(
        0, wc.lpszClassName, kWindowTitle,
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT,
        kWindowWidth, kWindowHeight,
        nullptr, nullptr, hInstance, nullptr);

    if (!g_hwnd) {
        printf("FAIL: CreateWindowExW failed (0x%08lx)\n", GetLastError());
        return 1;
    }

    // Initialize AGenUI engine
    if (!InitAGenUI()) {
        printf("FAIL: AGenUI initialization failed\n");
        return 1;
    }

    ShowWindow(g_hwnd, nCmdShow);
    UpdateWindow(g_hwnd);

    printf("[Playground] Window created, AGenUI initialized. Entering message loop.\n");

    // Phase 3.3: auto-run scenario from AGENUI_SCENARIO env var (1-4),
    // defaulting to scenario 1. Allows automated CLI verification of all
    // four scenarios without GUI input (F2/F3/F4/F5 still work manually).
    int initialScenario = 1;
    char envBuf[8] = {0};
    if (GetEnvironmentVariableA("AGENUI_SCENARIO", envBuf, sizeof(envBuf)) > 0) {
        int v = atoi(envBuf);
        if (v >= 1 && v <= 4) initialScenario = v;
    }
    g_initialScenario = initialScenario;

    // Auto-send A2UI protocol after 1.5s (for automated testing)
    SetTimer(g_hwnd, 1, 1500, nullptr);

    // Message loop
    MSG msg = {};
    while (msg.message != WM_QUIT) {
        if (PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        } else {
            OnRender();
        }
    }

    ShutdownAGenUI();
    DiscardDeviceResources();
    UnregisterClassW(L"AGenUIPlayground", hInstance);

    printf("[Playground] Exiting.\n");
    return static_cast<int>(msg.wParam);
}

// ---------------------------------------------------------------------------
// AGenUI Initialization
// ---------------------------------------------------------------------------
static bool InitAGenUI() {
    g_engine = agenuiInit();
    if (!g_engine) {
        printf("FAIL: agenuiInit() returned nullptr\n");
        return false;
    }
    printf("[AGenUI] agenuiInit() OK, version=%s\n", agenuiGetVersion());

    g_engine->registerFunction(R"({"name":"win_default"})", &g_platformFunc);
    printf("[AGenUI] PlatformFunction registered\n");

    g_surfaceMgr = g_engine->createSurfaceManager();
    if (!g_surfaceMgr) {
        printf("FAIL: createSurfaceManager() returned nullptr\n");
        return false;
    }
    printf("[AGenUI] createSurfaceManager() OK, instanceId=%d\n",
           g_surfaceMgr->getInstanceId());

    g_sizeProvider = new agenui_win::WindowsSurfaceSizeProvider(g_hwnd);
    g_surfaceMgr->setSurfaceSizeProvider(g_sizeProvider);

    g_surfaceMgr->addSurfaceEventListener(&g_listener);

    g_surfaceMgr->beginTextStream();
    printf("[AGenUI] beginTextStream() OK\n");

    return true;
}

static void ShutdownAGenUI() {
    if (g_surfaceMgr) {
        g_surfaceMgr->endTextStream();
        printf("[AGenUI] endTextStream() OK\n");

        g_surfaceMgr->removeSurfaceEventListener(&g_listener);

        if (g_engine) {
            g_engine->destroySurfaceManager(g_surfaceMgr);
            printf("[AGenUI] destroySurfaceManager() OK\n");
        }
        g_surfaceMgr = nullptr;
    }

    if (g_sizeProvider) {
        delete g_sizeProvider;
        g_sizeProvider = nullptr;
    }

    agenuiDestroy();
    printf("[AGenUI] agenuiDestroy() OK\n");
    g_engine = nullptr;
}

// ---------------------------------------------------------------------------
// Direct2D Resources
// ---------------------------------------------------------------------------
static bool CreateDeviceResources() {
    if (g_renderTarget) return true;

    HRESULT hr;

    RECT rc;
    GetClientRect(g_hwnd, &rc);
    D2D1_SIZE_U size = D2D1::SizeU(
        rc.right - rc.left,
        rc.bottom - rc.top);

    hr = agenui_win::D2DResources::Instance().D2DFactory()->CreateHwndRenderTarget(
        D2D1::RenderTargetProperties(),
        D2D1::HwndRenderTargetProperties(
            g_hwnd,
            size,
            D2D1_PRESENT_OPTIONS_NONE),
        &g_renderTarget);

    if (FAILED(hr)) {
        printf("FAIL: CreateHwndRenderTarget (0x%08lx)\n", hr);
        return false;
    }

    // Create a reusable text brush (dark)
    hr = g_renderTarget->CreateSolidColorBrush(
        D2D1::ColorF(D2D1::ColorF::DarkSlateGray),
        &g_textBrush);
    if (FAILED(hr)) {
        printf("FAIL: CreateSolidColorBrush(text) (0x%08lx)\n", hr);
        return false;
    }

    // Default text format for instructions
    hr = agenui_win::D2DResources::Instance().DWriteFactory()->CreateTextFormat(
        L"Segoe UI", nullptr,
        DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL,
        18.0f, L"", &g_defaultTextFormat);
    if (FAILED(hr)) {
        printf("FAIL: CreateTextFormat (0x%08lx)\n", hr);
        return false;
    }
    g_defaultTextFormat->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_CENTER);
    g_defaultTextFormat->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    printf("[D2D] Device resources created\n");
    return true;
}

static void DiscardDeviceResources() {
    // Phase 3.1: clear image cache before releasing the render target,
    // because cached ID2D1Bitmaps are device-dependent and would dangle.
    g_imageLoader.ClearCache();

    if (g_defaultTextFormat) { g_defaultTextFormat->Release(); g_defaultTextFormat = nullptr; }
    if (g_textBrush)  { g_textBrush->Release();  g_textBrush = nullptr; }
    if (g_renderTarget) { g_renderTarget->Release(); g_renderTarget = nullptr; }
    printf("[D2D] Device resources discarded\n");
}

// ---------------------------------------------------------------------------
// Component Rendering
// ---------------------------------------------------------------------------

// Render a Text component at its Yoga layout position
static void RenderText(const agenui_win::CapturedComponent& cc) {
    if (cc.text.empty()) return;

    float fontSize = cc.fontSize > 0 ? cc.fontSize : 24.0f;

    // Create per-component text format
    IDWriteTextFormat* tf = nullptr;
    HRESULT hr = agenui_win::D2DResources::Instance().DWriteFactory()->CreateTextFormat(
        L"Segoe UI", nullptr,
        DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL,
        fontSize, L"", &tf);
    if (FAILED(hr) || !tf) return;

    // Set alignment
    if (cc.textAlign == "center") {
        tf->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_CENTER);
        tf->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    } else if (cc.textAlign == "right") {
        tf->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_TRAILING);
    } else {
        tf->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_LEADING);
    }

    // Convert layout coordinates: a2ui logical units -> DIP (*2)
    // Engine's Yoga layout gives x/y but text components have width=0/height=0
    // because the engine doesn't have a text measurement function on Windows yet.
    // We use DirectWrite to measure text and compute width/height ourselves.
    D2D1_SIZE_F rtSize = g_renderTarget->GetSize();
    float dipX = cc.x * 2.0f;
    float dipY = cc.y * 2.0f;

    // Measure text with DirectWrite to get actual dimensions
    std::wstring wtext = ToWide(cc.text);
    IDWriteTextLayout* textLayout = nullptr;
    float dipW = rtSize.width;  // default: full width
    float dipH = fontSize * 1.5f; // default: 1.5x font size

    agenui_win::D2DResources::Instance().DWriteFactory()->CreateTextLayout(
        wtext.c_str(),
        static_cast<UINT32>(wtext.size()),
        tf,
        rtSize.width,  // max width constraint
        rtSize.height, // max height constraint
        &textLayout);
    if (textLayout) {
        DWRITE_TEXT_METRICS metrics;
        textLayout->GetMetrics(&metrics);
        dipW = metrics.widthIncludingTrailingWhitespace + 4.0f;  // small padding
        dipH = metrics.height + 4.0f;
        textLayout->Release();
    }

    // If Yoga gave us explicit width/height, use those instead
    if (cc.width > 0.0f) dipW = cc.width * 2.0f;
    if (cc.height > 0.0f) dipH = cc.height * 2.0f;

    // Resolve text color
    D2D1_COLOR_F textColor = ParseHexColor(cc.textColor, D2D1::ColorF(D2D1::ColorF::DarkSlateGray));

    // Create a brush with the resolved color
    ID2D1SolidColorBrush* textBrush = nullptr;
    g_renderTarget->CreateSolidColorBrush(textColor, &textBrush);
    if (textBrush) {
        g_renderTarget->DrawText(
            wtext.c_str(),
            static_cast<UINT32>(wtext.size()),
            tf,
            D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
            textBrush);
        textBrush->Release();
    }
    tf->Release();
}

// Render a Button component as a rounded rectangle + centered label
static void RenderButton(const agenui_win::CapturedComponent& cc) {
    // Convert layout coordinates
    float dipX = cc.x * 2.0f;
    float dipY = cc.y * 2.0f;
    float dipW = cc.width > 0 ? cc.width * 2.0f : 200.0f;
    float dipH = cc.height > 0 ? cc.height * 2.0f : 44.0f;

    // Resolve colors
    D2D1_COLOR_F bgColor = ParseHexColor(cc.bgColor, D2D1::ColorF(0.0f, 0.478f, 1.0f, 1.0f)); // default blue
    D2D1_COLOR_F borderColor = ParseHexColor(cc.borderColor, D2D1::ColorF(0.0f, 0.0f, 0.0f, 0.0f));

    // Phase 3.2: if this is the just-clicked button and the highlight window
    // is still active, tint the background green as visual click feedback.
    if (!g_highlightId.empty() && g_highlightId == cc.id) {
        ULONGLONG elapsed = GetTickCount() - g_highlightStartMs;
        if (elapsed < kHighlightDurationMs) {
            bgColor = D2D1::ColorF(0.16f, 0.8f, 0.27f, 1.0f); // green
        } else {
            g_highlightId.clear(); // expire
        }
    }

    // Create bg brush
    ID2D1SolidColorBrush* bgBrush = nullptr;
    g_renderTarget->CreateSolidColorBrush(bgColor, &bgBrush);

    // Fill rounded rectangle
    if (bgBrush) {
        float radius = cc.borderRadius * 2.0f; // a2ui -> DIP
        if (radius > 0.1f) {
            D2D1_ROUNDED_RECT rr = D2D1::RoundedRect(
                D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
                radius, radius);
            g_renderTarget->FillRoundedRectangle(rr, bgBrush);
        } else {
            g_renderTarget->FillRectangle(
                D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH), bgBrush);
        }
        bgBrush->Release();
    }

    // Draw border if present
    if (cc.borderWidth > 0.0f) {
        ID2D1SolidColorBrush* borderBrush = nullptr;
        g_renderTarget->CreateSolidColorBrush(borderColor, &borderBrush);
        if (borderBrush) {
            float bw = cc.borderWidth * 2.0f;
            float radius = cc.borderRadius * 2.0f;
            if (radius > 0.1f) {
                D2D1_ROUNDED_RECT rr = D2D1::RoundedRect(
                    D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
                    radius, radius);
                g_renderTarget->DrawRoundedRectangle(rr, borderBrush, bw);
            } else {
                g_renderTarget->DrawRectangle(
                    D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
                    borderBrush, bw);
            }
            borderBrush->Release();
        }
    }

    // Draw button label (centered)
    if (!cc.text.empty()) {
        IDWriteTextFormat* tf = nullptr;
        float labelSize = cc.fontSize > 0 ? cc.fontSize : 16.0f;
        agenui_win::D2DResources::Instance().DWriteFactory()->CreateTextFormat(
            L"Segoe UI", nullptr,
            DWRITE_FONT_WEIGHT_SEMI_BOLD, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL,
            labelSize, L"", &tf);
        if (tf) {
            tf->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_CENTER);
            tf->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

            // White text on colored background
            ID2D1SolidColorBrush* labelBrush = nullptr;
            g_renderTarget->CreateSolidColorBrush(D2D1::ColorF(D2D1::ColorF::White), &labelBrush);
            if (labelBrush) {
                std::wstring wtext = ToWide(cc.text);
                g_renderTarget->DrawText(
                    wtext.c_str(),
                    static_cast<UINT32>(wtext.size()),
                    tf,
                    D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
                    labelBrush);
                labelBrush->Release();
            }
            tf->Release();
        }
    }
}

// Render an Image component.
// Phase 3.1: attempts to load the real image via WIC → ID2D1Bitmap and draw
// it with DrawBitmap. Falls back to the Phase 2 gray rectangle + X when src
// is empty or loading fails.
static void RenderImage(const agenui_win::CapturedComponent& cc) {
    float dipX = cc.x * 2.0f;
    float dipY = cc.y * 2.0f;
    float dipW = cc.width > 0 ? cc.width * 2.0f : 100.0f;
    float dipH = cc.height > 0 ? cc.height * 2.0f : 100.0f;

    D2D1_RECT_F destRect = D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH);

    // Try to load a real bitmap. src may be a local path, file:/// URI, or
    // http(s) URL.
    ID2D1Bitmap* bitmap = nullptr;
    if (!cc.src.empty() && g_renderTarget) {
        bitmap = g_imageLoader.LoadFromUrl(g_renderTarget, cc.src);
    }

    if (bitmap) {
        // Compute source rectangle = full bitmap (so we don't stretch
        // partial pixels). Interpolation mode is left to the RT default
        // (linear), which is fine for the playground.
        D2D1_SIZE_F bmpSize = bitmap->GetSize();
        D2D1_RECT_F srcRect = D2D1::RectF(0.0f, 0.0f, bmpSize.width, bmpSize.height);

        g_renderTarget->DrawBitmap(
            bitmap,
            destRect,
            1.0f,                        // opacity
            D2D1_BITMAP_INTERPOLATION_MODE_LINEAR,
            srcRect);

        // Draw a thin border so the image edge is visible against white bg
        ID2D1SolidColorBrush* borderBrush = nullptr;
        g_renderTarget->CreateSolidColorBrush(
            D2D1::ColorF(0.6f, 0.6f, 0.6f, 1.0f), &borderBrush);
        if (borderBrush) {
            g_renderTarget->DrawRectangle(destRect, borderBrush, 1.0f);
            borderBrush->Release();
        }
        return;
    }

    // ----- Fallback: Phase 2 placeholder (gray rect + X) -----
    ID2D1SolidColorBrush* placeholderBrush = nullptr;
    g_renderTarget->CreateSolidColorBrush(
        D2D1::ColorF(0.85f, 0.85f, 0.85f, 1.0f), &placeholderBrush);
    if (placeholderBrush) {
        g_renderTarget->FillRectangle(destRect, placeholderBrush);

        // Draw border
        ID2D1SolidColorBrush* borderBrush = nullptr;
        g_renderTarget->CreateSolidColorBrush(
            D2D1::ColorF(0.6f, 0.6f, 0.6f, 1.0f), &borderBrush);
        if (borderBrush) {
            g_renderTarget->DrawRectangle(destRect, borderBrush, 1.0f);
            borderBrush->Release();
        }

        // Draw an X to indicate image placeholder
        ID2D1SolidColorBrush* xBrush = nullptr;
        g_renderTarget->CreateSolidColorBrush(
            D2D1::ColorF(0.5f, 0.5f, 0.5f, 1.0f), &xBrush);
        if (xBrush) {
            g_renderTarget->DrawLine(
                D2D1::Point2F(dipX, dipY),
                D2D1::Point2F(dipX + dipW, dipY + dipH), xBrush, 1.0f);
            g_renderTarget->DrawLine(
                D2D1::Point2F(dipX + dipW, dipY),
                D2D1::Point2F(dipX, dipY + dipH), xBrush, 1.0f);
            xBrush->Release();
        }

        placeholderBrush->Release();
    }

    // Draw "IMG" label
    IDWriteTextFormat* tf = nullptr;
    agenui_win::D2DResources::Instance().DWriteFactory()->CreateTextFormat(
        L"Segoe UI", nullptr,
        DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL,
        10.0f, L"", &tf);
    if (tf) {
        tf->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_CENTER);
        tf->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        ID2D1SolidColorBrush* labelBrush = nullptr;
        g_renderTarget->CreateSolidColorBrush(
            D2D1::ColorF(0.4f, 0.4f, 0.4f, 1.0f), &labelBrush);
        if (labelBrush) {
            std::wstring wlabel = L"IMG";
            g_renderTarget->DrawText(
                wlabel.c_str(),
                static_cast<UINT32>(wlabel.size()),
                tf,
                destRect,
                labelBrush);
            labelBrush->Release();
        }
        tf->Release();
    }
}

// ---------------------------------------------------------------------------
// Render
// ---------------------------------------------------------------------------
static void OnRender() {
    if (!CreateDeviceResources()) return;

    if (g_renderTarget->CheckWindowState() & D2D1_WINDOW_STATE_OCCLUDED)
        return;

    g_renderTarget->BeginDraw();

    // Clear background to white
    g_renderTarget->Clear(D2D1::ColorF(D2D1::ColorF::White));

    // Get captured components from the listener
    auto components = g_listener.getCapturedComponents();

    D2D1_SIZE_F rtSize = g_renderTarget->GetSize();

    if (components.empty()) {
        // No A2UI content yet — show instructions with all available keys
        std::wstring instructions =
            L"AGenUI Windows Playground - Phase 3\n\n"
            L"Waiting for A2UI protocol stream...\n\n"
            L"Available keys:\n"
            L"  F2: Column + Text/Button/Image\n"
            L"  F3: Row layout\n"
            L"  F4: Nested Column -> Row -> Column\n"
            L"  F5: Multi-button list\n"
            L"  ESC: Exit\n\n"
            L"Scenario 1 auto-runs after 1.5s.";
        g_renderTarget->DrawText(
            instructions.c_str(),
            static_cast<UINT32>(instructions.size()),
            g_defaultTextFormat,
            D2D1::RectF(0, 0, rtSize.width, rtSize.height),
            g_textBrush);
    } else {
        // Check if any component has non-zero layout coordinates
        bool hasLayout = false;
        for (const auto& cc : components) {
            if (cc.width > 0.0f || cc.height > 0.0f) {
                hasLayout = true;
                break;
            }
        }

        // If no layout coordinates from engine, fall back to manual stacking
        float stackY = 20.0f;

        for (const auto& cc : components) {
            if (cc.type == "Text") {
                RenderText(cc);
            } else if (cc.type == "Button") {
                RenderButton(cc);
            } else if (cc.type == "Image") {
                RenderImage(cc);
            }
            // Column/root containers are not rendered directly — their children are.
        }

        if (!hasLayout) {
            printf("[Render] Warning: No layout coordinates found, using fallback stacking\n");
        }
    }

    HRESULT hr = g_renderTarget->EndDraw();

    if (hr == D2DERR_RECREATE_TARGET) {
        printf("[D2D] Device lost (D2DERR_RECREATE_TARGET), discarding resources\n");
        DiscardDeviceResources();
        // DiscardDeviceResources already calls g_imageLoader.ClearCache(),
        // clearing all device-dependent cached bitmaps. Next OnRender()
        // will recreate the RT and re-decode images on demand.
    } else if (FAILED(hr)) {
        printf("[D2D] EndDraw failed (0x%08lx)\n", hr);
    }
}

// ---------------------------------------------------------------------------
// Phase 3.2: Button click interaction + event routing
// ---------------------------------------------------------------------------
// Coordinate pipeline (physical pixels → a2ui logical units):
//   1. WM_LBUTTONDOWN gives physical pixels
//   2. Convert to DIP:  dipX = pixelX / dpiScale
//   3. Convert to a2ui logical:  a2uiX = dipX / 2
// Then compare against component xywh (a2ui logical units, see
// win_message_listener.h parseComponent which reads x/y/width/height).
static float PixelsToA2uiLogical(int pixel, float dpiScale) {
    if (dpiScale <= 0.0f) dpiScale = 1.0f;
    return static_cast<float>(pixel) / dpiScale / 2.0f;
}

static float GetCurrentDpiScale() {
    HDC hdc = GetDC(g_hwnd);
    if (!hdc) return 1.0f;
    int dpi = GetDeviceCaps(hdc, LOGPIXELSX);
    ReleaseDC(g_hwnd, hdc);
    return (dpi > 0) ? (dpi / 96.0f) : 1.0f;
}

// Hit-test the captured components and, if a Button is hit, route an action
// event through the engine via ISurfaceManager::submitUIAction.
static void HandleLButtonDown(int pixelX, int pixelY) {
    float dpiScale = GetCurrentDpiScale();
    float a2uiX = PixelsToA2uiLogical(pixelX, dpiScale);
    float a2uiY = PixelsToA2uiLogical(pixelY, dpiScale);

    auto components = g_listener.getCapturedComponents();
    const agenui_win::CapturedComponent* hit = nullptr;

    for (const auto& cc : components) {
        if (cc.type != "Button") continue;
        // Hit-test against the Button's xywh (a2ui logical units).
        if (a2uiX >= cc.x && a2uiX <= cc.x + cc.width &&
            a2uiY >= cc.y && a2uiY <= cc.y + cc.height) {
            hit = &cc;
            break;
        }
    }

    if (!hit) {
        printf("[Click] Miss at a2ui=(%.1f, %.1f) — no button hit\n", a2uiX, a2uiY);
        return;
    }

    printf("[Click] Hit button '%s' at a2ui=(%.1f, %.1f) xywh=(%.1f,%.1f,%.1f,%.1f)\n",
           hit->id.c_str(), a2uiX, a2uiY,
           hit->x, hit->y, hit->width, hit->height);

    // Visual feedback: tint the clicked button green for ~400ms.
    g_highlightId = hit->id;
    g_highlightStartMs = GetTickCount();

    // Update the title bar with click info.
    wchar_t titleBuf[128];
    swprintf_s(titleBuf, _countof(titleBuf),
               L"AGenUI — Click: %hs @ (%d, %d)", hit->id.c_str(), pixelX, pixelY);
    SetWindowTextW(g_hwnd, titleBuf);

    // Route the action through the AGenUI engine. submitUIAction expects an
    // ActionMessage { surfaceId, sourceComponentId, contextJson }.
    if (g_surfaceMgr) {
        agenui::ActionMessage action;
        action.surfaceId = g_listener.getSurfaceId();
        action.sourceComponentId = hit->id;
        action.contextJson =
            std::string("{\"event\":\"click\",\"x\":") +
            std::to_string(a2uiX) +
            ",\"y\":" + std::to_string(a2uiY) + "}";
        g_surfaceMgr->submitUIAction(action);
        printf("[Click] submitUIAction sent: surfaceId=%s componentId=%s\n",
               action.surfaceId.c_str(), action.sourceComponentId.c_str());
    } else {
        printf("[Click] g_surfaceMgr is null, cannot route action\n");
    }
}

// Check whether the mouse is hovering over any captured Button and, if so,
// set the hand cursor. Called from WM_MOUSEMOVE.
static bool IsMouseOverButton(int pixelX, int pixelY) {
    if (!g_listener.hasSurface()) return false;

    float dpiScale = GetCurrentDpiScale();
    float a2uiX = PixelsToA2uiLogical(pixelX, dpiScale);
    float a2uiY = PixelsToA2uiLogical(pixelY, dpiScale);

    auto components = g_listener.getCapturedComponents();
    for (const auto& cc : components) {
        if (cc.type != "Button") continue;
        if (a2uiX >= cc.x && a2uiX <= cc.x + cc.width &&
            a2uiY >= cc.y && a2uiY <= cc.y + cc.height) {
            return true;
        }
    }
    return false;
}

// ---------------------------------------------------------------------------
// Window Resize
// ---------------------------------------------------------------------------
static void OnSize(UINT width, UINT height) {
    printf("[Resize] WM_SIZE: %u x %u (physical px)\n", width, height);

    if (g_renderTarget) {
        HRESULT hr = g_renderTarget->Resize(D2D1::SizeU(width, height));
        if (FAILED(hr)) {
            printf("[Resize] RenderTarget::Resize failed (0x%08lx)\n", hr);
        }
    }

    if (g_surfaceMgr) {
        float density = GetCurrentDpiScale();
        float widthDip  = static_cast<float>(width) / density;
        float heightDip = static_cast<float>(height) / density;

        agenui::SurfaceLayoutInfo info;
        info.surfaceId = "main";
        info.width  = widthDip / 2.0f;
        info.height = heightDip / 2.0f;

        printf("[Resize] Notifying engine: surface=%.1f x %.1f (a2ui logical)\n",
               info.width, info.height);
        g_surfaceMgr->onSurfaceSizeChanged(info);
    }
}

// ---------------------------------------------------------------------------
// Phase 3.3: Multi-scenario A2UI protocol tests
// ---------------------------------------------------------------------------
// Each scenario sends a full A2UI stream: endTextStream → beginTextStream →
// createSurface → updateComponents → endTextStream. The window title and
// console log are updated per scenario so manual verification is easy.
//
// Scenario index → F-key mapping:
//   1 → F2 (Column + Text + Button + Image, the original Phase 2 stream)
//   2 → F3 (Row horizontal layout)
//   3 → F4 (Nested Column → Row → Column)
//   4 → F5 (Multi-button list with different colors/radii)

static void SendScenario(int scenarioIndex) {
    if (!g_surfaceMgr) return;

    const char* scenarioName = "";
    const wchar_t* titleText = L"";

    g_surfaceMgr->endTextStream();
    g_surfaceMgr->beginTextStream();

    // Common: create surface
    g_surfaceMgr->receiveTextChunk(
        R"({"version":"v0.9","createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"}})");

    switch (scenarioIndex) {
    case 1:
        scenarioName = "Column + Text + Button + Image";
        titleText = L"AGenUI - F2: Column + Text/Button/Image";
        printf("[Scenario 1] Column root → Text(title/subtitle/body) + Button + Image\n");
        g_surfaceMgr->receiveTextChunk(
            R"({"version":"v0.9","updateComponents":{"surfaceId":"main","components":[)"
            R"({"id":"root","component":"Column","children":["title","subtitle","body","actionBtn","heroImage"],"align":"center","justify":"center"},)"
            R"({"id":"title","component":"Text","text":"Hello AGenUI!","variant":"h1","styles":{"font-size":"48px","text-align":"center","color":"#000000"}},)"
            R"({"id":"subtitle","component":"Text","text":"Windows Playground Phase 3","variant":"h3","styles":{"font-size":"24px","text-align":"center","color":"#000000E6"}},)"
            R"({"id":"body","component":"Text","text":"Text + Button + Image rendered via Direct2D using Yoga layout coordinates.","variant":"body","styles":{"font-size":"16px","text-align":"center","color":"#000000E6"}},)"
            R"({"id":"actionBtn","component":"Button","text":"Click Me","styles":{"background-color":"#007DFF","border-radius":"8px","border-width":"0px","padding":"12px","font-size":"16px"}},)"
            R"({"id":"heroImage","component":"Image","src":"C:/Windows/Web/Wallpaper/Windows/img0.jpg","styles":{"width":"200px","height":"120px"}})"
            R"(]}})");
        break;

    case 2:
        scenarioName = "Row horizontal layout";
        titleText = L"AGenUI - F3: Row layout";
        printf("[Scenario 2] Row root → Text(left) + Button(center) + Text(right)\n");
        g_surfaceMgr->receiveTextChunk(
            R"({"version":"v0.9","updateComponents":{"surfaceId":"main","components":[)"
            R"({"id":"root","component":"Row","children":["left","center","right"],"align":"center","justify":"space-between"},)"
            R"({"id":"left","component":"Text","text":"Left","styles":{"font-size":"20px"}},)"
            R"({"id":"center","component":"Button","text":"Center","styles":{"background-color":"#007DFF","border-radius":"8px"}},)"
            R"({"id":"right","component":"Text","text":"Right","styles":{"font-size":"20px"}})"
            R"(]}})");
        break;

    case 3:
        scenarioName = "Nested Column → Row → Column";
        titleText = L"AGenUI - F4: Nested Column/Row/Column";
        printf("[Scenario 3] Column root → Row(middle) → [Text(a), Button(b), Text(c)]\n");
        g_surfaceMgr->receiveTextChunk(
            R"({"version":"v0.9","updateComponents":{"surfaceId":"main","components":[)"
            R"({"id":"root","component":"Column","children":["header","middle"],"align":"center","justify":"center"},)"
            R"({"id":"header","component":"Text","text":"Nested Layout Demo","variant":"h2","styles":{"font-size":"28px","text-align":"center","color":"#000000"}},)"
            R"({"id":"middle","component":"Row","children":["a","b","c"],"align":"center","justify":"space-around","styles":{"width":"400px","height":"80px"}},)"
            R"({"id":"a","component":"Text","text":"A","styles":{"font-size":"24px","color":"#007DFF"}},)"
            R"({"id":"b","component":"Button","text":"B","styles":{"background-color":"#34C759","border-radius":"12px","border-width":"0px"}},)"
            R"({"id":"c","component":"Text","text":"C","styles":{"font-size":"24px","color":"#FF3B30"}})"
            R"(]}})");
        break;

    case 4:
        scenarioName = "Multi-button list";
        titleText = L"AGenUI - F5: Multi-button list";
        printf("[Scenario 4] Column root → Text(title) + 3 Buttons (different colors/radii)\n");
        g_surfaceMgr->receiveTextChunk(
            R"({"version":"v0.9","updateComponents":{"surfaceId":"main","components":[)"
            R"({"id":"root","component":"Column","children":["title","btn1","btn2","btn3"],"align":"center","justify":"center"},)"
            R"({"id":"title","component":"Text","text":"Multi-Button List","variant":"h2","styles":{"font-size":"28px","text-align":"center","color":"#000000"}},)"
            R"({"id":"btn1","component":"Button","text":"Primary","styles":{"background-color":"#007DFF","border-radius":"8px","border-width":"0px","padding":"10px"}},)"
            R"({"id":"btn2","component":"Button","text":"Success","styles":{"background-color":"#34C759","border-radius":"20px","border-width":"0px","padding":"10px"}},)"
            R"({"id":"btn3","component":"Button","text":"Danger","styles":{"background-color":"#FF3B30","border-radius":"0px","border-width":"0px","padding":"10px"}})"
            R"(]}})");
        break;

    default:
        printf("[Scenario] Unknown scenario index: %d\n", scenarioIndex);
        g_surfaceMgr->endTextStream();
        return;
    }

    g_surfaceMgr->endTextStream();
    printf("[Scenario %d] '%s' stream sent — onComponentsAdd should follow\n",
           scenarioIndex, scenarioName);

    SetWindowTextW(g_hwnd, titleText);

    // Phase 3.2: extend auto-quit to 30s so the user can manually test
    // resize and click interactions without the window closing.
    SetTimer(g_hwnd, 2, 30000, nullptr);
}


// ---------------------------------------------------------------------------
// Window Procedure
// ---------------------------------------------------------------------------
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_SIZE:
        OnSize(LOWORD(lParam), HIWORD(lParam));
        return 0;

    case WM_PAINT:
        OnRender();
        ValidateRect(hwnd, nullptr);
        return 0;

    case WM_KEYDOWN:
        if (wParam == VK_F2) {
            SendScenario(1);
        } else if (wParam == VK_F3) {
            SendScenario(2);
        } else if (wParam == VK_F4) {
            SendScenario(3);
        } else if (wParam == VK_F5) {
            SendScenario(4);
        } else if (wParam == VK_ESCAPE) {
            PostQuitMessage(0);
        }
        return 0;

    case WM_LBUTTONDOWN: {
        int pixelX = GET_X_LPARAM(lParam);
        int pixelY = GET_Y_LPARAM(lParam);
        HandleLButtonDown(pixelX, pixelY);
        return 0;
    }

    case WM_MOUSEMOVE: {
        int pixelX = GET_X_LPARAM(lParam);
        int pixelY = GET_Y_LPARAM(lParam);
        bool overButton = IsMouseOverButton(pixelX, pixelY);

        // Lazily load the hand cursor once.
        if (!g_handCursor) {
            g_handCursor = LoadCursor(nullptr, IDC_HAND);
        }

        // Only call SetCursor on transitions to avoid per-pixel overhead.
        if (overButton && !g_isHoveringButton) {
            SetCursor(g_handCursor);
            g_isHoveringButton = true;
        } else if (!overButton && g_isHoveringButton) {
            SetCursor(LoadCursor(nullptr, IDC_ARROW));
            g_isHoveringButton = false;
        } else if (overButton && g_isHoveringButton) {
            // Still over a button — keep reinforcing the hand cursor.
            SetCursor(g_handCursor);
        }
        return 0;
    }

    case WM_SETCURSOR: {
        // Ensure the hand cursor stays during mouse capture / while the
        // cursor is over the client area and hovering a button.
        if (g_isHoveringButton && g_handCursor) {
            SetCursor(g_handCursor);
            return TRUE;
        }
        break; // fall through to DefWindowProc for default cursor handling
    }

    case WM_TIMER:
        if (wParam == 1) {
            KillTimer(hwnd, 1);
            // Auto-run the configured initial scenario on startup (1.5s timer).
            SendScenario(g_initialScenario);
        }
        if (wParam == 2) {
            KillTimer(hwnd, 2);
            printf("[Playground] Auto-quit timer fired\n");
            PostQuitMessage(0);
        }
        return 0;

    case WM_DPICHANGED: {
        RECT* const prcNewWindow = reinterpret_cast<RECT*>(lParam);
        SetWindowPos(hwnd, nullptr,
                     prcNewWindow->left, prcNewWindow->top,
                     prcNewWindow->right - prcNewWindow->left,
                     prcNewWindow->bottom - prcNewWindow->top,
                     SWP_NOZORDER | SWP_NOACTIVATE);
        return 0;
    }

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }

    return DefWindowProc(hwnd, msg, wParam, lParam);
}
