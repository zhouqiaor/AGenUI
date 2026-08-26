#pragma once

// D2DResources — singleton holding Direct2D / DirectWrite / WIC factories.
// Pattern: FactorySingleton + ComPtr (ref: CodeProject Direct2D Tutorial)
// All COM factories are created once and shared process-wide.

#include <d2d1.h>
#include <dwrite.h>
#include <wincodec.h>
#include <wrl/client.h>

#pragma comment(lib, "d2d1.lib")
#pragma comment(lib, "dwrite.lib")
#pragma comment(lib, "windowscodecs.lib")

namespace agenui_win {

class D2DResources {
public:
    static D2DResources& Instance() {
        static D2DResources instance;
        return instance;
    }

    ID2D1Factory*       D2DFactory()    { return m_d2dFactory.Get(); }
    IDWriteFactory*     DWriteFactory()  { return m_dWriteFactory.Get(); }
    IWICImagingFactory* WICFactory()     { return m_wicFactory.Get(); }

    // Prevent copies.
    D2DResources(const D2DResources&) = delete;
    D2DResources& operator=(const D2DResources&) = delete;

private:
    D2DResources() {
        // Create D2D factory (single-threaded — AGenUI engine runs on main thread)
        D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED,
                           m_d2dFactory.GetAddressOf());

        // Create DirectWrite factory
        DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED,
                            __uuidof(IDWriteFactory),
                            reinterpret_cast<IUnknown**>(m_dWriteFactory.GetAddressOf()));

        // Create WIC factory
        CoCreateInstance(CLSID_WICImagingFactory, nullptr,
                         CLSCTX_INPROC_SERVER,
                         IID_PPV_ARGS(m_wicFactory.GetAddressOf()));
    }

    ~D2DResources() = default;

    Microsoft::WRL::ComPtr<ID2D1Factory>       m_d2dFactory;
    Microsoft::WRL::ComPtr<IDWriteFactory>     m_dWriteFactory;
    Microsoft::WRL::ComPtr<IWICImagingFactory> m_wicFactory;
};

} // namespace agenui_win
