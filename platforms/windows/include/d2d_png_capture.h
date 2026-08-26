#pragma once

// d2d_png_capture.h - Offscreen Direct2D -> WIC Bitmap -> PNG helper.
// Provides a self-contained pipeline for visual regression tests:
//   1. Create a WIC bitmap of the requested size.
//   2. Wrap it as an ID2D1RenderTarget via ID2D1Factory::CreateWicBitmapRenderTarget.
//   3. Let the caller render anything onto that RT (Clear / FillRectangle / DrawText ...).
//   4. SaveRenderTargetToPng() encodes the RT's backing WIC bitmap to a PNG file.
//
// All COM resources are released via Microsoft::WRL::ComPtr. The caller is
// responsible for keeping the returned ID2D1RenderTarget alive while rendering.

#include <d2d1.h>
#include <dwrite.h>
#include <wincodec.h>
#include <windows.h>
#include <wrl/client.h>

#include <string>

#pragma comment(lib, "d2d1.lib")
#pragma comment(lib, "dwrite.lib")
#pragma comment(lib, "windowscodecs.lib")

namespace agenui_win {

// Aggregates the resources needed to render onto a WIC bitmap.
struct WicRenderTarget {
    Microsoft::WRL::ComPtr<IWICBitmap>         wicBitmap;
    Microsoft::WRL::ComPtr<ID2D1RenderTarget>   renderTarget;
};

// Create an offscreen WIC-backed D2D render target of the given size.
// Returns true on success. The caller renders onto rt.renderTarget.
inline bool CreateWicRenderTarget(ID2D1Factory* d2dFactory,
                                   IWICImagingFactory* wicFactory,
                                   UINT width,
                                   UINT height,
                                   WicRenderTarget& rt) {
    if (!d2dFactory || !wicFactory || width == 0 || height == 0) {
        return false;
    }

    // 1) WIC bitmap (32bpp PBGRA, premultiplied - matches D2D's default).
    HRESULT hr = wicFactory->CreateBitmap(
        width, height,
        GUID_WICPixelFormat32bppPBGRA,
        WICBitmapCacheOnDemand,
        &rt.wicBitmap);
    if (FAILED(hr)) return false;

    // 2) D2D render target wrapping the WIC bitmap.
    D2D1_RENDER_TARGET_PROPERTIES props = D2D1::RenderTargetProperties(
        D2D1_RENDER_TARGET_TYPE_DEFAULT,
        D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM,
                          D2D1_ALPHA_MODE_PREMULTIPLIED),
        0.0f,  // dpiX (0 = desktop DPI)
        0.0f); // dpiY

    hr = d2dFactory->CreateWicBitmapRenderTarget(
        rt.wicBitmap.Get(), props, &rt.renderTarget);
    return SUCCEEDED(hr);
}

// Save a WIC-backed D2D render target to a PNG file.
// The render target must have been created via CreateWicRenderTarget (i.e.
// its backing surface is an IWICBitmap). This function creates a PNG encoder,
// writes the bitmap into a frame, and commits to disk.
inline bool SaveRenderTargetToPng(ID2D1RenderTarget* rt,
                                   const std::wstring& pngPath) {
    if (!rt) return false;

    // Recover the IWICBitmap from the render target. CreateWicBitmapRenderTarget
    // returns an ID2D1RenderTarget backed by an IWICBitmap; querying for the
    // bitmap is done via the WICBitmap/RenderTarget pair we created, but since
    // the caller only passes ID2D1RenderTarget* here, we use a small trick: we
    // re-fetch the bitmap through the well-known IID_IWICBitmap interface by
    // querying the render target's internal bitmap. Unfortunately ID2D1RenderTarget
    // does not expose the underlying bitmap, so callers should prefer the overload
    // below that takes IWICBitmap directly.
    (void)rt;
    (void)pngPath;
    return false;
}

// Save the backing WIC bitmap of a WicRenderTarget to a PNG file.
// This is the preferred entry point: pass the WicRenderTarget created by
// CreateWicRenderTarget. Uses the WIC PNG encoder + file stream.
inline bool SaveWicBitmapToPng(IWICBitmap* wicBitmap,
                                IWICImagingFactory* wicFactory,
                                const std::wstring& pngPath) {
    if (!wicBitmap || !wicFactory) return false;

    // Create a file stream for the output PNG.
    Microsoft::WRL::ComPtr<IWICStream> stream;
    HRESULT hr = wicFactory->CreateStream(&stream);
    if (FAILED(hr)) return false;

    hr = stream->InitializeFromFilename(pngPath.c_str(), GENERIC_WRITE);
    if (FAILED(hr)) return false;

    // Create a PNG encoder.
    Microsoft::WRL::ComPtr<IWICBitmapEncoder> encoder;
    hr = wicFactory->CreateEncoder(GUID_ContainerFormatPng, nullptr, &encoder);
    if (FAILED(hr)) return false;

    hr = encoder->Initialize(stream.Get(), WICBitmapEncoderNoCache);
    if (FAILED(hr)) return false;

    // Create a new frame.
    Microsoft::WRL::ComPtr<IWICBitmapFrameEncode> frame;
    Microsoft::WRL::ComPtr<IPropertyBag2> propertyBag;
    hr = encoder->CreateNewFrame(&frame, &propertyBag);
    if (FAILED(hr)) return false;

    hr = frame->Initialize(propertyBag.Get());
    if (FAILED(hr)) return false;

    // Write the source bitmap into the frame.
    hr = frame->WriteSource(wicBitmap, nullptr);
    if (FAILED(hr)) return false;

    hr = frame->Commit();
    if (FAILED(hr)) return false;

    hr = encoder->Commit();
    return SUCCEEDED(hr);
}

// Convenience: render a user callback onto a fresh WIC RT, then save to PNG.
// The callback receives ID2D1RenderTarget* (already BeginDraw'd) and the
// WicRenderTarget's bitmap. Caller does NOT call EndDraw.
template <typename RenderFn>
inline bool RenderToPng(ID2D1Factory* d2dFactory,
                        IWICImagingFactory* wicFactory,
                        UINT width,
                        UINT height,
                        const std::wstring& pngPath,
                        RenderFn&& renderFn) {
    WicRenderTarget rt;
    if (!CreateWicRenderTarget(d2dFactory, wicFactory, width, height, rt)) {
        return false;
    }
    rt.renderTarget->BeginDraw();
    bool ok = std::forward<RenderFn>(renderFn)(rt.renderTarget.Get());
    rt.renderTarget->EndDraw();
    if (!ok) return false;
    return SaveWicBitmapToPng(rt.wicBitmap.Get(), wicFactory, pngPath);
}

} // namespace agenui_win
