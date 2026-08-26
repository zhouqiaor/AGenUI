#pragma once

// WicImageLoader — Phase 3.1 WIC image loading → ID2D1Bitmap.
//
// Replaces the Phase 2 Image placeholder (gray rectangle + X) with real
// Direct2D bitmap rendering. Follows the Microsoft official WIC pipeline:
//   1. IWICImagingFactory::CreateDecoderFromFilename → IWICBitmapDecoder
//   2. IWICBitmapDecoder::GetFrame(0) → IWICBitmapFrameDecode
//   3. IWICImagingFactory::CreateFormatConverter → IWICFormatConverter
//   4. IWICFormatConverter::Initialize(frame, GUID_WICPixelFormat32bppPBGRA, ...)
//   5. ID2D1RenderTarget::CreateBitmapFromWicBitmap(converter) → ID2D1Bitmap
//   6. ID2D1RenderTarget::DrawBitmap(bitmap, destRect) (caller side)
//
// Reference: docs/research/P3-WINUI3-WIC-REFERENCE.md §3.5 / §5.
// Pattern: FactorySingleton (D2DResources) + ComPtr + cached raw pointers.
// C++17, header-only, UTF-8, no new third-party deps (Windows SDK only).

#include "d2d_resources.h"

#include <d2d1.h>
#include <wincodec.h>
#include <wrl/client.h>
#include <windows.h>

#include <stdio.h>
#include <string>
#include <unordered_map>
#include <mutex>

namespace agenui_win {

class WicImageLoader {
public:
    WicImageLoader() = default;
    ~WicImageLoader() { ClearCache(); }

    WicImageLoader(const WicImageLoader&) = delete;
    WicImageLoader& operator=(const WicImageLoader&) = delete;

    // Load an image from a local file path → cached ID2D1Bitmap.
    // Returns nullptr on failure (caller should fall back to placeholder).
    // Keyed by lowercased absolute path so duplicate components share one
    // bitmap. Not thread-safe w.r.t. the render target; call from the
    // UI/render thread only (AGenUI engine runs single-threaded on main).
    ID2D1Bitmap* LoadFromFile(ID2D1RenderTarget* rt, const std::wstring& filePath) {
        if (!rt || filePath.empty()) return nullptr;

        std::wstring key = NormalizeKey(filePath);
        {
            std::lock_guard<std::mutex> lock(m_mutex);
            auto it = m_cache.find(key);
            if (it != m_cache.end() && it->second) {
                return it->second;  // cache hit
            }
        }

        ID2D1Bitmap* bitmap = DecodeFromFile(rt, filePath);
        if (!bitmap) return nullptr;

        std::lock_guard<std::mutex> lock(m_mutex);
        // Re-check under lock: another caller may have inserted the same key.
        auto it = m_cache.find(key);
        if (it != m_cache.end() && it->second) {
            bitmap->Release();
            return it->second;
        }
        m_cache[key] = bitmap;
        return bitmap;
    }

    // Load from a URL string. P3.1 supports:
    //   - file:/// prefix → strip and treat as local path
    //   - bare local path (drive letter or UNC)
    //   - http:// / https:// → URLDownloadToFileW to a temp file, then decode
    // Returns nullptr on any failure (network error, decode error, etc.).
    ID2D1Bitmap* LoadFromUrl(ID2D1RenderTarget* rt, const std::string& url) {
        if (!rt || url.empty()) return nullptr;

        // file:/// URI
        if (url.rfind("file:///", 0) == 0) {
            std::string path = url.substr(8); // strip "file:///"
            // Percent-encoded file URIs are out of scope for P3.1; treat
            // as a plain path. Forward slashes work on Win32 APIs.
            return LoadFromFile(rt, ToWide(path));
        }

        // http:// or https://
        if (url.rfind("http://", 0) == 0 || url.rfind("https://", 0) == 0) {
            std::wstring tempPath = DownloadToTemp(url);
            if (tempPath.empty()) {
                printf("[WIC] download failed for %s\n", url.c_str());
                return nullptr;
            }
            ID2D1Bitmap* bmp = LoadFromFile(rt, tempPath);
            // LoadFromFile caches by temp path; the temp file stays on disk
            // until ClearCache (acceptable for a playground). Do NOT delete
            // here because the bitmap may still hold a reference to the file
            // via WIC's lazy decode in some scenarios.
            return bmp;
        }

        // Bare path (assume local file)
        return LoadFromFile(rt, ToWide(url));
    }

    // Release all cached bitmaps. Call on D2DERR_RECREATE_TARGET / device loss
    // so we don't hold stale device-dependent bitmaps.
    void ClearCache() {
        std::lock_guard<std::mutex> lock(m_mutex);
        for (auto& kv : m_cache) {
            if (kv.second) {
                kv.second->Release();
                kv.second = nullptr;
            }
        }
        m_cache.clear();
        printf("[WIC] image cache cleared (%zu entries)\n", m_cache.size());
    }

private:
    // Decode a local file through the WIC pipeline → ID2D1Bitmap.
    ID2D1Bitmap* DecodeFromFile(ID2D1RenderTarget* rt, const std::wstring& path) {
        IWICImagingFactory* wic = GetWicFactory();
        if (!wic) return nullptr;

        // 1. Create decoder from file path
        Microsoft::WRL::ComPtr<IWICBitmapDecoder> decoder;
        HRESULT hr = wic->CreateDecoderFromFilename(
            path.c_str(),
            nullptr,
            GENERIC_READ,
            WICDecodeMetadataCacheOnLoad,
            decoder.GetAddressOf());
        if (FAILED(hr) || !decoder) {
            printf("[WIC] CreateDecoderFromFilename failed (0x%08lx) path=%ls\n",
                   hr, path.c_str());
            return nullptr;
        }

        // 2. Get frame 0
        Microsoft::WRL::ComPtr<IWICBitmapFrameDecode> frame;
        hr = decoder->GetFrame(0, frame.GetAddressOf());
        if (FAILED(hr) || !frame) {
            printf("[WIC] GetFrame failed (0x%08lx)\n", hr);
            return nullptr;
        }

        // 3-4. Format convert to 32bpp PBGRA (D2D's preferred pixel format)
        Microsoft::WRL::ComPtr<IWICFormatConverter> converter;
        hr = wic->CreateFormatConverter(converter.GetAddressOf());
        if (FAILED(hr) || !converter) {
            printf("[WIC] CreateFormatConverter failed (0x%08lx)\n", hr);
            return nullptr;
        }

        hr = converter->Initialize(
            frame.Get(),
            GUID_WICPixelFormat32bppPBGRA,
            WICBitmapDitherTypeNone,
            nullptr,
            0.0,
            WICBitmapPaletteTypeCustom);
        if (FAILED(hr)) {
            printf("[WIC] FormatConverter::Initialize failed (0x%08lx)\n", hr);
            return nullptr;
        }

        // 5. Create D2D bitmap from WIC bitmap source
        ID2D1Bitmap* bitmap = nullptr;
        hr = rt->CreateBitmapFromWicBitmap(
            converter.Get(),
            nullptr,  // default D2D1_BITMAP_PROPERTIES
            &bitmap);
        if (FAILED(hr) || !bitmap) {
            printf("[WIC] CreateBitmapFromWicBitmap failed (0x%08lx)\n", hr);
            return nullptr;
        }

        return bitmap;
    }

    // Lazy access to the shared WIC factory from D2DResources.
    IWICImagingFactory* GetWicFactory() {
        return D2DResources::Instance().WICFactory();
    }

    // Download URL → temp file path via URLDownloadToFileW (urlmon.lib).
    // Returns empty wstring on failure.
    std::wstring DownloadToTemp(const std::string& url) {
        std::wstring wurl = ToWide(url);

        // Build a unique temp path
        wchar_t tempDir[MAX_PATH] = {0};
        if (GetTempPathW(MAX_PATH, tempDir) == 0) return L"";

        // Derive a filename from the URL tail, fallback to a generic name
        std::string tail = ExtractFileTail(url);
        std::wstring wtail = ToWide(tail);
        if (wtail.empty()) wtail = L"agenui_img";

        std::wstring tempPath = std::wstring(tempDir) + L"agenui_" + wtail;

        // URLDownloadToFileW is blocking; fine for P3.1 playground.
        // Synchronous, returns S_OK on success.
        HRESULT hr = URLDownloadToFileW(
            nullptr,
            wurl.c_str(),
            tempPath.c_str(),
            0,
            nullptr);
        if (FAILED(hr)) {
            printf("[WIC] URLDownloadToFileW failed (0x%08lx) url=%s\n",
                   hr, url.c_str());
            return L"";
        }
        return tempPath;
    }

    // Extract a usable filename tail from a URL (after last '/', strip query).
    static std::string ExtractFileTail(const std::string& url) {
        size_t slash = url.find_last_of('/');
        std::string tail = (slash == std::string::npos) ? url : url.substr(slash + 1);
        size_t q = tail.find_first_of('?');
        if (q != std::string::npos) tail = tail.substr(0, q);
        // Keep only basic filename chars
        std::string out;
        for (char c : tail) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out += c;
            }
        }
        if (out.empty()) out = "img";
        return out;
    }

    // Lowercase + collapse as a cache key. We don't canonicalize the path
    // (GetFullPathName) because the playground passes small fixed paths.
    static std::wstring NormalizeKey(const std::wstring& path) {
        std::wstring k = path;
        for (auto& c : k) {
            if (c >= L'A' && c <= L'Z') c = c - L'A' + L'a';
        }
        return k;
    }

    // UTF-8 → wide
    static std::wstring ToWide(const std::string& utf8) {
        if (utf8.empty()) return L"";
        int wlen = MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, nullptr, 0);
        if (wlen <= 0) return L"";
        std::wstring wstr(static_cast<size_t>(wlen), 0);
        MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, &wstr[0], wlen);
        if (!wstr.empty() && wstr.back() == L'\0') wstr.pop_back();
        return wstr;
    }

    std::mutex m_mutex;
    std::unordered_map<std::wstring, ID2D1Bitmap*> m_cache;
};

} // namespace agenui_win
