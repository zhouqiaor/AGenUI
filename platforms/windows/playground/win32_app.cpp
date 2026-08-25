// AGenUI Windows Playground — Phase 2: Multi-component rendering with Yoga layout
//
// Upgrades Phase 1.5 to render Text + Button + Image components using
// Yoga layout coordinates output by the AGenUI engine.
//
// 1. Creates a Win32 window (per-Monitor DPI aware)
// 2. Initializes Direct2D HwndRenderTarget
// 3. Initializes AGenUI engine + SurfaceManager
// 4. Auto-timer sends A2UI JSON (createSurface + updateComponents with
//    Column root containing Text, Button, and Image children)
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

#include <windows.h>
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

static const wchar_t* kWindowTitle = L"AGenUI Windows Playground - Phase 2";
static const int kWindowWidth  = 800;
static const int kWindowHeight = 600;

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

// Render an Image component as a placeholder rectangle with an X
// (Phase 2: no actual image loading — WIC decoding is Phase 3+)
static void RenderImage(const agenui_win::CapturedComponent& cc) {
    float dipX = cc.x * 2.0f;
    float dipY = cc.y * 2.0f;
    float dipW = cc.width > 0 ? cc.width * 2.0f : 100.0f;
    float dipH = cc.height > 0 ? cc.height * 2.0f : 100.0f;

    // Draw a light gray placeholder
    ID2D1SolidColorBrush* placeholderBrush = nullptr;
    g_renderTarget->CreateSolidColorBrush(
        D2D1::ColorF(0.85f, 0.85f, 0.85f, 1.0f), &placeholderBrush);
    if (placeholderBrush) {
        g_renderTarget->FillRectangle(
            D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH), placeholderBrush);

        // Draw border
        ID2D1SolidColorBrush* borderBrush = nullptr;
        g_renderTarget->CreateSolidColorBrush(
            D2D1::ColorF(0.6f, 0.6f, 0.6f, 1.0f), &borderBrush);
        if (borderBrush) {
            g_renderTarget->DrawRectangle(
                D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
                borderBrush, 1.0f);
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

    // Draw "Image" label
    if (!cc.src.empty()) {
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
                // Truncate src to fit
                std::string label = "IMG";
                std::wstring wlabel = ToWide(label);
                g_renderTarget->DrawText(
                    wlabel.c_str(),
                    static_cast<UINT32>(wlabel.size()),
                    tf,
                    D2D1::RectF(dipX, dipY, dipX + dipW, dipY + dipH),
                    labelBrush);
                labelBrush->Release();
            }
            tf->Release();
        }
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
        // No A2UI content yet — show instructions
        std::wstring instructions = L"AGenUI Windows Playground - Phase 2\n\n"
                                    L"Waiting for A2UI protocol stream...\n\n"
                                    L"Components will appear here once the protocol is sent.";
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
    } else if (FAILED(hr)) {
        printf("[D2D] EndDraw failed (0x%08lx)\n", hr);
    }
}

// ---------------------------------------------------------------------------
// Window Resize
// ---------------------------------------------------------------------------
static void OnSize(UINT width, UINT height) {
    if (g_renderTarget) {
        g_renderTarget->Resize(D2D1::SizeU(width, height));
    }

    if (g_surfaceMgr) {
        float density = 1.0f;
        HDC hdc = GetDC(g_hwnd);
        if (hdc) {
            int dpi = GetDeviceCaps(hdc, LOGPIXELSX);
            density = (dpi > 0) ? (dpi / 96.0f) : 1.0f;
            ReleaseDC(g_hwnd, hdc);
        }
        float widthDip  = static_cast<float>(width) / density;
        float heightDip = static_cast<float>(height) / density;

        agenui::SurfaceLayoutInfo info;
        info.surfaceId = "main";
        info.width  = widthDip / 2.0f;
        info.height = heightDip / 2.0f;
        g_surfaceMgr->onSurfaceSizeChanged(info);
    }
}

// ---------------------------------------------------------------------------
// Test: send A2UI protocol with Text + Button + Image components
// ---------------------------------------------------------------------------
static void SendTestTextStream() {
    if (!g_surfaceMgr) return;

    printf("[Playground] Sending A2UI protocol stream (Phase 2: Text + Button + Image)...\n");

    g_surfaceMgr->endTextStream();
    g_surfaceMgr->beginTextStream();

    // 1. Create surface
    g_surfaceMgr->receiveTextChunk(
        R"({"version":"v0.9","createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"}})");

    // 2. Update components: Column root with Text + Button + Image children
    g_surfaceMgr->receiveTextChunk(
        R"({"version":"v0.9","updateComponents":{"surfaceId":"main","components":[)"
        R"({"id":"root","component":"Column","children":["title","subtitle","body","actionBtn","heroImage"],"align":"center","justify":"center"},)"
        R"({"id":"title","component":"Text","text":"Hello AGenUI!","variant":"h1","styles":{"font-size":"48px","text-align":"center","color":"#000000"}},)"
        R"({"id":"subtitle","component":"Text","text":"Windows Playground Phase 2","variant":"h3","styles":{"font-size":"24px","text-align":"center","color":"#000000E6"}},)"
        R"({"id":"body","component":"Text","text":"Text + Button + Image rendered via Direct2D using Yoga layout coordinates.","variant":"body","styles":{"font-size":"16px","text-align":"center","color":"#000000E6"}},)"
        R"({"id":"actionBtn","component":"Button","text":"Click Me","styles":{"background-color":"#007DFF","border-radius":"8px","border-width":"0px","padding":"12px","font-size":"16px"}},)"
        R"({"id":"heroImage","component":"Image","src":"https://example.com/hero.png","styles":{"width":"200px","height":"120px"}})"
        R"(]}})");

    g_surfaceMgr->endTextStream();
    printf("[Playground] A2UI protocol stream sent\n");

    // Auto-quit after 3s (for automated CI testing)
    SetTimer(g_hwnd, 2, 3000, nullptr);
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
            SendTestTextStream();
        }
        if (wParam == VK_ESCAPE) {
            PostQuitMessage(0);
        }
        return 0;

    case WM_TIMER:
        if (wParam == 1) {
            KillTimer(hwnd, 1);
            SendTestTextStream();
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
