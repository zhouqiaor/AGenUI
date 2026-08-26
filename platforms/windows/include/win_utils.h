#pragma once

// win_utils.h — Shared utility functions for the AGenUI Windows playground
// and tests. Extracted from win32_app.cpp so that unit tests can call
// ParseHexColor / ToWide directly without depending on file-scope statics.

#include <windows.h>
#include <d2d1.h>
#include <cstdio>
#include <string>

namespace agenui_win {

// Parse hex color string ("#RRGGBB" or "#RRGGBBAA") to D2D1_COLOR_F.
// Returns defaultColor on empty/invalid input.
inline D2D1_COLOR_F ParseHexColor(const std::string& hex, D2D1_COLOR_F defaultColor) {
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

// Convert UTF-8 to wide string.
inline std::wstring ToWide(const std::string& utf8) {
    if (utf8.empty()) return L"";
    int wlen = MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, nullptr, 0);
    std::wstring wstr(wlen, 0);
    MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, &wstr[0], wlen);
    if (!wstr.empty() && wstr.back() == L'\0') wstr.pop_back();
    return wstr;
}

} // namespace agenui_win
