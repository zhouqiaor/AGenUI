// AGenUI Windows P2 - D2D -> WIC -> PNG visual regression tests
// Renders primitives onto an offscreen WIC bitmap render target and saves
// them as PNG files. The tests verify the pipeline does not crash and the
// output PNG file exists and is non-empty. No pixel-level golden comparison
// is performed (unsuitable for CI environments).

#include "d2d_resources.h"
#include "d2d_png_capture.h"
#include "win_utils.h"

#include <gtest/gtest.h>
#include <windows.h>
#include <d2d1.h>
#include <dwrite.h>
#include <wincodec.h>
#include <wrl/client.h>

#include <cstdio>
#include <filesystem>
#include <string>
#include <system_error>

using agenui_win::D2DResources;
using agenui_win::WicRenderTarget;
using agenui_win::CreateWicRenderTarget;
using agenui_win::SaveWicBitmapToPng;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

// RAII wrapper around CoInitializeEx / CoUninitialize.
class CoInitializer {
public:
    CoInitializer() { CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED); }
    ~CoInitializer() { CoUninitialize(); }
    CoInitializer(const CoInitializer&) = delete;
    CoInitializer& operator=(const CoInitializer&) = delete;
};

// Global COM init for the test binary (one per process is enough).
namespace {
CoInitializer g_comInit;
}

// Return the directory where the test exe lives, with trailing backslash.
static std::wstring ExeDir() {
    wchar_t path[MAX_PATH] = {0};
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    std::wstring p(path);
    size_t slash = p.find_last_of(L"\\/");
    return (slash == std::wstring::npos) ? L"" : p.substr(0, slash + 1);
}

// Return true if a file exists and reports its size via st_size.
static bool FileExistsWithSize(const std::wstring& path, uintmax_t& size) {
    std::error_code ec;
    auto status = std::filesystem::status(path, ec);
    if (ec) return false;
    if (!std::filesystem::is_regular_file(status)) return false;
    size = std::filesystem::file_size(path, ec);
    return !ec;
}

// Build a full path under the exe directory.
static std::wstring PngPath(const std::wstring& name) {
    return ExeDir() + name;
}

// ---------------------------------------------------------------------------
// 1. Blue rectangle -> PNG
// ---------------------------------------------------------------------------
TEST(VisualTest, BlueRectangleToPng) {
    const std::wstring png = PngPath(L"visual_blue_rect.png");

    // Delete any stale file so we verify the pipeline actually writes.
    DeleteFileW(png.c_str());

    auto ok = agenui_win::RenderToPng(
        D2DResources::Instance().D2DFactory(),
        D2DResources::Instance().WICFactory(),
        200, 150,
        png,
        [](ID2D1RenderTarget* rt) -> bool {
            // Clear background to white then paint a blue rect.
            rt->Clear(D2D1::ColorF(1.0f, 1.0f, 1.0f, 1.0f));

            Microsoft::WRL::ComPtr<ID2D1SolidColorBrush> brush;
            if (FAILED(rt->CreateSolidColorBrush(
                    D2D1::ColorF(0.0f, 0.0f, 1.0f, 1.0f), &brush))) {
                return false;
            }
            rt->FillRectangle(
                D2D1::RectF(20.0f, 20.0f, 180.0f, 130.0f),
                brush.Get());
            return true;
        });

    ASSERT_TRUE(ok) << "RenderToPng pipeline failed";

    uintmax_t size = 0;
    ASSERT_TRUE(FileExistsWithSize(png, size))
        << "PNG file not found at " << png;
    EXPECT_GT(size, 0u) << "PNG file is empty";
}

// ---------------------------------------------------------------------------
// 2. Text "Hello" -> PNG
// ---------------------------------------------------------------------------
TEST(VisualTest, TextHelloToPng) {
    const std::wstring png = PngPath(L"visual_text_hello.png");
    DeleteFileW(png.c_str());

    auto ok = agenui_win::RenderToPng(
        D2DResources::Instance().D2DFactory(),
        D2DResources::Instance().WICFactory(),
        300, 100,
        png,
        [](ID2D1RenderTarget* rt) -> bool {
            rt->Clear(D2D1::ColorF(1.0f, 1.0f, 1.0f, 1.0f));

            IDWriteTextFormat* tf = nullptr;
            if (FAILED(D2DResources::Instance().DWriteFactory()->CreateTextFormat(
                    L"Segoe UI", nullptr,
                    DWRITE_FONT_WEIGHT_NORMAL,
                    DWRITE_FONT_STYLE_NORMAL,
                    DWRITE_FONT_STRETCH_NORMAL,
                    32.0f, L"", &tf))) {
                return false;
            }

            Microsoft::WRL::ComPtr<ID2D1SolidColorBrush> brush;
            if (FAILED(rt->CreateSolidColorBrush(
                    D2D1::ColorF(0.0f, 0.0f, 0.0f, 1.0f), &brush))) {
                tf->Release();
                return false;
            }

            const std::wstring text = L"Hello";
            rt->DrawText(
                text.c_str(),
                static_cast<UINT32>(text.size()),
                tf,
                D2D1::RectF(10.0f, 10.0f, 290.0f, 90.0f),
                brush.Get());
            tf->Release();
            return true;
        });

    ASSERT_TRUE(ok) << "RenderToPng pipeline failed";

    uintmax_t size = 0;
    ASSERT_TRUE(FileExistsWithSize(png, size))
        << "PNG file not found at " << png;
    EXPECT_GT(size, 0u) << "PNG file is empty";
}

// ---------------------------------------------------------------------------
// 3. Rounded rectangle (Button mock) -> PNG
// ---------------------------------------------------------------------------
TEST(VisualTest, RoundedRectangleButtonToPng) {
    const std::wstring png = PngPath(L"visual_rounded_button.png");
    DeleteFileW(png.c_str());

    auto ok = agenui_win::RenderToPng(
        D2DResources::Instance().D2DFactory(),
        D2DResources::Instance().WICFactory(),
        250, 80,
        png,
        [](ID2D1RenderTarget* rt) -> bool {
            rt->Clear(D2D1::ColorF(1.0f, 1.0f, 1.0f, 1.0f));

            Microsoft::WRL::ComPtr<ID2D1SolidColorBrush> bgBrush;
            if (FAILED(rt->CreateSolidColorBrush(
                    D2D1::ColorF(0.0f, 0.478f, 1.0f, 1.0f), &bgBrush))) {
                return false;
            }

            // Rounded rectangle mimicking a button (#007DFF bg, 8px radius).
            D2D1_ROUNDED_RECT rr = D2D1::RoundedRect(
                D2D1::RectF(25.0f, 18.0f, 225.0f, 62.0f),
                8.0f, 8.0f);
            rt->FillRoundedRectangle(rr, bgBrush.Get());

            // Draw a centered white label "OK".
            IDWriteTextFormat* tf = nullptr;
            if (FAILED(D2DResources::Instance().DWriteFactory()->CreateTextFormat(
                    L"Segoe UI", nullptr,
                    DWRITE_FONT_WEIGHT_SEMI_BOLD,
                    DWRITE_FONT_STYLE_NORMAL,
                    DWRITE_FONT_STRETCH_NORMAL,
                    16.0f, L"", &tf))) {
                return false;
            }
            tf->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_CENTER);
            tf->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

            Microsoft::WRL::ComPtr<ID2D1SolidColorBrush> labelBrush;
            if (FAILED(rt->CreateSolidColorBrush(
                    D2D1::ColorF(1.0f, 1.0f, 1.0f, 1.0f), &labelBrush))) {
                tf->Release();
                return false;
            }

            const std::wstring label = L"OK";
            rt->DrawText(
                label.c_str(),
                static_cast<UINT32>(label.size()),
                tf,
                D2D1::RectF(25.0f, 18.0f, 225.0f, 62.0f),
                labelBrush.Get());
            tf->Release();
            return true;
        });

    ASSERT_TRUE(ok) << "RenderToPng pipeline failed";

    uintmax_t size = 0;
    ASSERT_TRUE(FileExistsWithSize(png, size))
        << "PNG file not found at " << png;
    EXPECT_GT(size, 0u) << "PNG file is empty";
}
