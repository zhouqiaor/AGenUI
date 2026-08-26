#pragma once

// WindowsSurfaceSizeProvider — ISurfaceSizeProvider implementation backed by
// the HWND client rect. The engine queries this synchronously when it needs
// the surface size for layout.
//
// Pattern ref: Harmony A2UISurfaceSizeProvider (a2ui_platform_layout_bridge.h)

#include "agenui_surface_size_provider.h"
#include <windows.h>

namespace agenui_win {

class WindowsSurfaceSizeProvider : public agenui::ISurfaceSizeProvider {
public:
    explicit WindowsSurfaceSizeProvider(HWND hwnd) : m_hwnd(hwnd) {}

    void setHwnd(HWND hwnd) { m_hwnd = hwnd; }

    agenui::SurfaceSize getSurfaceSize(const std::string& surfaceId) override {
        if (!m_hwnd) return {0.0f, 0.0f};

        RECT rc;
        if (!GetClientRect(m_hwnd, &rc)) return {0.0f, 0.0f};

        // Convert physical pixels to a2ui logical units (pv * 2).
        // For Phase 1 we use 1:1 mapping (density = 2.0 means 1px = 0.5 pv).
        // AGenUI uses "pv * 2" as logical units; with DPI = 96 (100%):
        //   1 DIP = 1 physical pixel
        //   a2ui logical unit = pv * 2, and 1 pv = 1 DIP at 100% scale
        // So logical width = physical_width / 2.0
        float density = getDpiScale();
        float widthDip  = static_cast<float>(rc.right - rc.left) / density;
        float heightDip = static_cast<float>(rc.bottom - rc.top) / density;

        return { widthDip / 2.0f, heightDip / 2.0f };
    }

private:
    float getDpiScale() const {
        if (!m_hwnd) return 1.0f;
        HDC hdc = GetDC(m_hwnd);
        if (!hdc) return 1.0f;
        int dpi = GetDeviceCaps(hdc, LOGPIXELSX);
        ReleaseDC(m_hwnd, hdc);
        return (dpi > 0) ? (dpi / 96.0f) : 1.0f;
    }

    HWND m_hwnd = nullptr;
};

} // namespace agenui_win
