#pragma once

// WindowsPlatformFunction — minimal IPlatformFunction implementation.
// Phase 1: returns Success with empty data for all calls.
// Phase 2+: implement actual platform function dispatch.

#include "agenui_platform_function.h"

namespace agenui_win {

class WindowsPlatformFunction : public agenui::IPlatformFunction {
public:
    agenui::FunctionCallResult callSync(
        const agenui::FunctionCallContext& context,
        const std::string& params) override
    {
        (void)context;
        (void)params;
        agenui::FunctionCallResult result;
        result.status = agenui::FunctionCallStatus::Success;
        result.data = "{}";
        return result;
    }
};

} // namespace agenui_win
