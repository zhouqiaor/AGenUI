#pragma once

// WindowsMessageListener — IAGenUIMessageListener implementation.
// Phase 2: captures all component types (Text, Button, Image, Column) from
// onComponentsAdd / onComponentsUpdate, including Yoga layout coordinates
// (x, y, width, height) output by the engine. Thread-safe.

#include "agenui_message_listener.h"
#include <cstdio>
#include <string>
#include <vector>
#include <mutex>
#include <nlohmann/json.hpp>

namespace agenui_win {

// A unified component captured from the engine's component add/update callbacks.
struct CapturedComponent {
    std::string id;
    std::string type;       // "Text", "Button", "Image", "Column", etc.
    std::string parentId;

    // Common properties
    std::string text;       // Text or Button label

    // Text-specific
    float fontSize = 24.0f;     // DIP units
    std::string textAlign;      // "left", "center", "right"
    std::string textColor;      // hex color like "#000000" or "#000000E6"

    // Button-specific
    std::string bgColor;        // background-color hex
    float borderRadius = 0.0f;  // border-radius in DIP
    float borderWidth = 0.0f;   // border-width in DIP
    std::string borderColor;    // border-color hex
    float padding = 0.0f;       // padding in DIP

    // Image-specific
    std::string src;            // image source URL/path

    // Layout coordinates from Yoga (a2ui logical units, need *2 for DIP)
    float x = 0.0f;
    float y = 0.0f;
    float width = 0.0f;
    float height = 0.0f;

    bool hasStyles = false;
};

class WindowsMessageListener : public agenui::IAGenUIMessageListener {
public:
    // Access captured components (thread-safe copy).
    std::vector<CapturedComponent> getCapturedComponents() {
        std::lock_guard<std::mutex> lock(m_mutex);
        return m_components;
    }

    bool hasSurface() const { return m_surfaceCreated; }
    const std::string& getSurfaceId() const { return m_surfaceId; }

    void onCreateSurface(const agenui::CreateSurfaceMessage& msg) override {
        printf("[Listener] onCreateSurface: surfaceId=%s catalogId=%s\n",
               msg.surfaceId.c_str(), msg.catalogId.c_str());
        std::lock_guard<std::mutex> lock(m_mutex);
        m_surfaceId = msg.surfaceId;
        m_surfaceCreated = true;
        m_components.clear();
    }

    void onDeleteSurface(const agenui::DeleteSurfaceMessage& msg) override {
        printf("[Listener] onDeleteSurface: surfaceId=%s\n",
               msg.surfaceId.c_str());
        std::lock_guard<std::mutex> lock(m_mutex);
        m_surfaceCreated = false;
        m_components.clear();
    }

    void onComponentsUpdate(const std::string& surfaceId,
                           const std::vector<agenui::ComponentsUpdateMessage>& msg) override {
        printf("[Listener] onComponentsUpdate: surfaceId=%s count=%zu\n",
               surfaceId.c_str(), msg.size());

        std::lock_guard<std::mutex> lock(m_mutex);
        m_components.clear();

        for (const auto& item : msg) {
            parseComponent(item.component, "");
        }
    }

    void onComponentsAdd(const std::string& surfaceId,
                        const std::vector<agenui::ComponentsAddMessage>& msg) override {
        printf("[Listener] onComponentsAdd: surfaceId=%s count=%zu\n",
               surfaceId.c_str(), msg.size());

        std::lock_guard<std::mutex> lock(m_mutex);

        for (const auto& item : msg) {
            parseComponent(item.component, item.parentId);
        }
    }

    void onComponentsRemove(const std::string& surfaceId,
                           const std::vector<agenui::ComponentsRemoveMessage>& msg) override {
        printf("[Listener] onComponentsRemove: surfaceId=%s count=%zu\n",
               surfaceId.c_str(), msg.size());
    }

    void onActionEventRouted(const std::string& content) override {
        printf("[Listener] onActionEventRouted: %s\n", content.c_str());
    }

    void onError(const agenui::ErrorMessage& msg) override {
        printf("[Listener] onError: code=%d surfaceId=%s message=%s\n",
               msg.code, msg.surfaceId.c_str(), msg.message.c_str());
    }

private:
    // Parse a component JSON string and extract type-specific properties
    // + Yoga layout coordinates from the styles block.
    void parseComponent(const std::string& componentJson, const std::string& parentId) {
        try {
            auto json = nlohmann::json::parse(componentJson);
            std::string type = json.value("component", "");
            std::string id = json.value("id", "");

            printf("  [Component] id=%s type=%s parentId=%s\n",
                   id.c_str(), type.c_str(), parentId.c_str());

            CapturedComponent cc;
            cc.id = id;
            cc.type = type;
            cc.parentId = parentId;

            // Common: text (used by Text and Button)
            cc.text = json.value("text", "");

            // Parse styles
            if (json.contains("styles")) {
                cc.hasStyles = true;
                const auto& styles = json["styles"];

                // Text-specific
                if (styles.contains("font-size")) {
                    cc.fontSize = parsePixelValue(styles["font-size"].get<std::string>(), 24.0f);
                }
                if (styles.contains("text-align")) {
                    cc.textAlign = styles["text-align"].get<std::string>();
                }
                if (styles.contains("color")) {
                    cc.textColor = styles["color"].get<std::string>();
                }

                // Button-specific
                if (styles.contains("background-color")) {
                    cc.bgColor = styles["background-color"].get<std::string>();
                }
                if (styles.contains("border-radius")) {
                    cc.borderRadius = parsePixelValue(styles["border-radius"].get<std::string>(), 0.0f);
                }
                if (styles.contains("border-width")) {
                    cc.borderWidth = parsePixelValue(styles["border-width"].get<std::string>(), 0.0f);
                }
                if (styles.contains("border-color")) {
                    cc.borderColor = styles["border-color"].get<std::string>();
                }
                if (styles.contains("padding")) {
                    cc.padding = parsePixelValue(styles["padding"].get<std::string>(), 0.0f);
                }

                // Yoga layout coordinates (a2ui logical units)
                if (styles.contains("x")) {
                    cc.x = parseFloatValue(styles["x"]);
                }
                if (styles.contains("y")) {
                    cc.y = parseFloatValue(styles["y"]);
                }
                if (styles.contains("width")) {
                    cc.width = parseFloatValue(styles["width"]);
                }
                if (styles.contains("height")) {
                    cc.height = parseFloatValue(styles["height"]);
                }
            }

            // Image-specific
            if (json.contains("src")) {
                cc.src = json["src"].get<std::string>();
            }

            // Log component details
            if (type == "Text") {
                printf("    text='%s' fontSize=%.0f align=%s color=%s xywh=(%.1f,%.1f,%.1f,%.1f)\n",
                       cc.text.c_str(), cc.fontSize, cc.textAlign.c_str(),
                       cc.textColor.c_str(), cc.x, cc.y, cc.width, cc.height);
            } else if (type == "Button") {
                printf("    text='%s' bg=%s radius=%.0f border=%.0f padding=%.0f xywh=(%.1f,%.1f,%.1f,%.1f)\n",
                       cc.text.c_str(), cc.bgColor.c_str(), cc.borderRadius,
                       cc.borderWidth, cc.padding, cc.x, cc.y, cc.width, cc.height);
            } else if (type == "Image") {
                printf("    src='%s' xywh=(%.1f,%.1f,%.1f,%.1f)\n",
                       cc.src.c_str(), cc.x, cc.y, cc.width, cc.height);
            } else {
                printf("    xywh=(%.1f,%.1f,%.1f,%.1f)\n",
                       cc.x, cc.y, cc.width, cc.height);
            }

            m_components.push_back(std::move(cc));
        } catch (const std::exception& e) {
            printf("  [Parse Error] %s\n", e.what());
        }
    }

    static float parsePixelValue(const std::string& s, float defaultVal) {
        // Parse "32px" or "32" to float
        std::string num;
        for (char c : s) {
            if (std::isdigit(c) || c == '.') num += c;
            else break;
        }
        if (num.empty()) return defaultVal;
        try { return std::stof(num); } catch (...) { return defaultVal; }
    }

    // Parse a JSON value that may be a number or a string like "100.5px"
    static float parseFloatValue(const nlohmann::json& val) {
        if (val.is_number()) {
            return val.get<float>();
        }
        if (val.is_string()) {
            return parsePixelValue(val.get<std::string>(), 0.0f);
        }
        return 0.0f;
    }

    std::mutex m_mutex;
    bool m_surfaceCreated = false;
    std::string m_surfaceId;
    std::vector<CapturedComponent> m_components;
};

} // namespace agenui_win
